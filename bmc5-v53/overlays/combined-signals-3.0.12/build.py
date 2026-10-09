#!/usr/bin/env python3
"""Build and test a local 3.0.12 HUD/signal JAR from the exact QA-passed 3.0.11 JAR."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import zipfile


BASE_SHA256 = "4a5fdc0a58b0d453509260c628970c974ad341c08fca9f359c5e27ccc56665d2"
LEGACY_SHA256 = "eb4b1685205ba1ac3432b1708572d829e9004193ca8857b8b05798bc48f6ce12"
RUNTIME = "pl/aridlin/psychiatrykroles/runtime/"
SOURCE_STEMS = (
    "HudSchema", "HudServer", "RuntimeNetwork", "StableRuntime", "SignalRate",
    "SignalSchema", "SignalServer", "SignalState", "SignalValues",
    "client/HudClient", "client/HudMotion", "client/RuntimeClient", "client/SignalClient",
    "client/VisualExpressions",
)
REPLACEMENTS = frozenset(RUNTIME + name for name in (
    "HudSchema.class", "HudSchema$Message.class", "HudSchema$Node.class", "HudSchema$Scene.class",
    "HudServer.class", "RuntimeNetwork.class", "RuntimeNetwork$Action.class",
    "RuntimeNetwork$Snapshot.class", "StableRuntime.class", "client/HudClient.class",
    "client/HudClient$PreparedNode.class", "client/HudClient$PreparedScene.class",
    "client/RuntimeClient.class", "client/VisualExpressions.class",
    "client/VisualExpressions$Budget.class", "client/VisualExpressions$Expression.class",
))
ADDITIONS = frozenset(RUNTIME + name for name in (
    "RuntimeNetwork$Capabilities.class", "client/HudMotion.class", "SignalRate.class", "SignalSchema.class",
    "SignalSchema$Change.class", "SignalSchema$Message.class", "SignalSchema$Value.class",
    "SignalServer.class", "SignalServer$PlayerState.class", "SignalState.class",
    "SignalValues.class", "client/SignalClient.class",
))
TESTS = (
    "SignalProtocolTest", "SignalStateTest", "SignalValuesTest", "SignalRateTest",
    "SignalHudCompatibilityTest", "HudProtocolTest", "pl.aridlin.fixture.HudMotionTest",
    "pl.aridlin.fixture.VisualExpressionsTest",
)
MUSIC_CLASSES = (
    "pl/aridlin/psychiatrykroles/music/MusicBackgroundMixin.class",
    "pl/aridlin/psychiatrykroles/audio/BackgroundMusicPauseState.class",
    "pl/aridlin/psychiatrykroles/audio/MusicAudibility.class",
    "pl/aridlin/psychiatrykroles/audio/VanillaJukeboxAudio.class",
)
OLD_MUSIC_CLASSES = (
    "pl/aridlin/psychiatrykroles/music/BackgroundMusicPauseState.class",
    "pl/aridlin/psychiatrykroles/music/MusicAudibility.class",
    "pl/aridlin/psychiatrykroles/music/VanillaJukeboxAudio.class",
)


def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def require_hash(path: Path, expected: str) -> None:
    found = digest(path.read_bytes())
    if found != expected:
        raise ValueError(f"{path}: expected SHA-256 {expected}, found {found}")


def read_jar(path: Path) -> dict[str, bytes]:
    with zipfile.ZipFile(path) as archive:
        names = archive.namelist()
        if len(names) != len(set(names)) or archive.testzip() is not None:
            raise ValueError("Input JAR has duplicate or corrupt entries")
        return {name: archive.read(name) for name in names if not name.endswith("/")}


def java_tools() -> tuple[str, str]:
    root = Path("/usr/lib/jvm/java-21-openjdk/bin")
    return (str(root / "javac") if (root / "javac").is_file() else "javac",
            str(root / "java") if (root / "java").is_file() else "java")


def compile_sources(base: Path, sdk: str, output: Path) -> tuple[dict[str, bytes], dict[str, str]]:
    stable = Path(__file__).resolve().parents[1] / "stable-runtime"
    sources = [stable / "src" / (RUNTIME + stem + ".java") for stem in SOURCE_STEMS]
    if any(not source.is_file() for source in sources):
        raise ValueError("A required runtime source is missing")
    classes = output / "classes"
    shutil.rmtree(classes, ignore_errors=True)
    classes.mkdir(parents=True)
    javac, _ = java_tools()
    result = subprocess.run([javac, "-J-Xmx384m", "--release", "21", "-proc:none",
                             "-cp", f"{base.resolve()}:{sdk}", "-d", str(classes),
                             *map(str, sources)], capture_output=True, text=True)
    (output / "compile.log").write_text(result.stdout + result.stderr)
    if result.returncode:
        raise RuntimeError("Runtime Java compile failed; see compile.log")
    compiled = {file.relative_to(classes).as_posix(): file.read_bytes()
                for file in classes.rglob("*.class")}
    expected = REPLACEMENTS | ADDITIONS
    if set(compiled) != expected:
        raise ValueError(f"Compiled class allowlist mismatch: missing={sorted(expected - set(compiled))}, "
                         f"extra={sorted(set(compiled) - expected)}")
    return compiled, {str(source.relative_to(stable)): digest(source.read_bytes())
                      for source in sources}


def write_jar(entries: dict[str, bytes], destination: Path) -> None:
    with zipfile.ZipFile(destination, "w", zipfile.ZIP_DEFLATED) as archive:
        for name, data in sorted(entries.items()):
            item = zipfile.ZipInfo(name, (2026, 10, 9, 8, 0, 0))
            item.compress_type = zipfile.ZIP_DEFLATED
            item.external_attr = 0o100644 << 16
            archive.writestr(item, data)


def verify_tests(candidate: Path, legacy: Path, sdk: str, output: Path) -> list[str]:
    stable = Path(__file__).resolve().parents[1] / "stable-runtime"
    javac, java = java_tools()
    classes = output / "test-classes"
    shutil.rmtree(classes, ignore_errors=True)
    classes.mkdir()
    sources = [stable / "tests" / (name.rsplit(".", 1)[-1] + ".java") for name in TESTS]
    subprocess.run([javac, "-J-Xmx384m", "--release", "21", "-proc:none",
                    "-cp", f"{candidate.resolve()}:{sdk}", "-d", str(classes),
                    *map(str, sources)], check=True)
    results = []
    for test in TESTS:
        result = subprocess.run([java, "-Xmx256m", "-XX:-UsePerfData", "-cp",
                                 f"{classes}:{candidate.resolve()}:{sdk}", test],
                                capture_output=True, text=True)
        if result.returncode:
            raise RuntimeError(f"{test} failed: {result.stdout}{result.stderr}")
        results.append(result.stdout.strip())
    old_classes = output / "legacy-test-classes"
    shutil.rmtree(old_classes, ignore_errors=True)
    old_classes.mkdir()
    subprocess.run([javac, "-J-Xmx256m", "--release", "21", "-proc:none",
                    "-cp", f"{legacy.resolve()}:{sdk}", "-d", str(old_classes),
                    str(stable / "tests/HudLegacyCompatibilityTest.java")], check=True)
    result = subprocess.run([java, "-Xmx256m", "-XX:-UsePerfData", "-cp",
                             f"{legacy.resolve()}:{old_classes}:{sdk}",
                             "HudLegacyCompatibilityTest"], capture_output=True, text=True)
    if result.returncode:
        raise RuntimeError(f"Legacy client parser test failed: {result.stdout}{result.stderr}")
    results.append(result.stdout.strip())
    return results


def build(base: Path, legacy: Path, sdk_file: Path, output: Path) -> dict:
    require_hash(base, BASE_SHA256)
    require_hash(legacy, LEGACY_SHA256)
    sdk = sdk_file.read_text().strip()
    if not sdk:
        raise ValueError("Empty SDK classpath")
    output.mkdir(parents=True, exist_ok=True)
    before = read_jar(base)
    if not REPLACEMENTS <= before.keys() or ADDITIONS & before.keys():
        raise ValueError("3.0.11 base does not have the expected runtime class layout")
    if any(name not in before for name in MUSIC_CLASSES) or any(name in before for name in OLD_MUSIC_CLASSES):
        raise ValueError("3.0.11 music relocation is missing")
    compiled, sources = compile_sources(base, sdk, output)
    entries = dict(before)
    entries.update(compiled)
    metadata_name = "META-INF/neoforge.mods.toml"
    metadata = entries[metadata_name].decode("utf-8")
    if metadata.count('version="3.0.11-bmc5"') != 6:
        raise ValueError("Unexpected 3.0.11 metadata version count")
    entries[metadata_name] = metadata.replace('version="3.0.11-bmc5"',
                                              'version="3.0.12-bmc5"').encode("utf-8")
    permitted = REPLACEMENTS | {metadata_name}
    changed = {name for name in before if before[name] != entries.get(name)}
    added = set(entries) - set(before)
    if not changed <= permitted or added != ADDITIONS or set(before) - set(entries):
        raise ValueError("Archive change exceeds the class and metadata allowlist")
    candidate = output / "psychiatryk_roles-3.0.12-bmc5.jar"
    write_jar(entries, candidate)
    final = read_jar(candidate)
    if final != entries or any(final[name] != before[name] for name in MUSIC_CLASSES):
        raise ValueError("Candidate archive roundtrip or music byte preservation failed")
    results = verify_tests(candidate, legacy, sdk, output)
    proof = {
        "base_sha256": BASE_SHA256,
        "candidate_sha256": digest(candidate.read_bytes()),
        "candidate_bytes": candidate.stat().st_size,
        "metadata_version": "3.0.12-bmc5",
        "replaced_classes": sorted(changed - {metadata_name}),
        "added_classes": sorted(added),
        "music_classes_preserved": {name: digest(before[name]) for name in MUSIC_CLASSES},
        "unchanged_base_entries": len(before) - len(changed),
        "source_sha256": sources,
        "tests": results,
        "game_client_launched": False,
        "deployed": False,
    }
    (output / "build-proof.json").write_text(json.dumps(proof, indent=2) + "\n")
    return proof


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base", type=Path, required=True, help="QA-passed 3.0.11 JAR")
    parser.add_argument("--legacy", type=Path, required=True, help="canonical 3.0.10 JAR")
    parser.add_argument("--sdk", type=Path, required=True, help="sdk-clean.txt")
    parser.add_argument("--output", type=Path, default=Path(__file__).resolve().parent / "build")
    args = parser.parse_args()
    result = build(args.base, args.legacy, args.sdk, args.output)
    print(json.dumps(result, indent=2))
