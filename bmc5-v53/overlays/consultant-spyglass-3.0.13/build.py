#!/usr/bin/env python3
"""Build a narrowly scoped 3.0.13 consultant spyglass candidate from live 3.0.12 bytes."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import zipfile


BASE_SHA256 = "6fbde292e6e88a0fa250966203c173ca06ebf77b36d1530eab4de82763f12566"
PRESET = "pl/aridlin/psychiatrykroles/CustomPresets.class"
ROLES = "pl/aridlin/psychiatrykroles/PsychiatrykRoles.class"
AUDIO = "pl/aridlin/kukirin/ScooterAudioClient.class"
META = "META-INF/neoforge.mods.toml"
HERE = Path(__file__).resolve().parent
SOURCE = HERE.parents[2] / "s23-v34/components/roles-unified/src/pl/aridlin/psychiatrykroles/CustomPresets.java"


def sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def run(args: list[str], log: Path) -> None:
    process = subprocess.run(args, capture_output=True, text=True)
    log.write_text(process.stdout + process.stderr)
    if process.returncode:
        raise RuntimeError(f"Command failed ({process.returncode}): {' '.join(args[:2])}; see {log}")


def build(base: Path, sdk_file: Path, output: Path) -> dict:
    if sha(base.read_bytes()) != BASE_SHA256:
        raise ValueError("Input is not the verified live 3.0.12 JAR")
    sdk = sdk_file.read_text().strip()
    if not sdk or not SOURCE.is_file():
        raise ValueError("Missing SDK or authored preset source")
    output.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(base) as archive:
        names = archive.namelist()
        if len(names) != len(set(names)) or archive.testzip() is not None:
            raise ValueError("Corrupt or ambiguous input JAR")
        entries = {name: archive.read(name) for name in names if not name.endswith("/")}
    if not {PRESET, ROLES, AUDIO, META} <= entries.keys():
        raise ValueError("Input JAR is missing an expected class or metadata")

    javac = "/usr/lib/jvm/java-21-openjdk/bin/javac"
    java = "/usr/lib/jvm/java-21-openjdk/bin/java"
    if not Path(javac).is_file(): javac = "javac"
    if not Path(java).is_file(): java = "java"
    classes = output / "classes"
    shutil.rmtree(classes, ignore_errors=True)
    classes.mkdir()
    starter = HERE.parents[2] / "s23-v19/build-inputs/goplanska-kinker-starter-1.0.2.jar"
    if not starter.is_file():
        raise ValueError("Missing original Kinker Starter compile dependency")
    run([javac, "-J-Xmx320m", "--release", "21", "-proc:none", "-cp", f"{base.resolve()}:{starter}:{sdk}",
         "-d", str(classes), str(SOURCE)], output / "preset-compile.log")
    compiled = classes / PRESET
    if not compiled.is_file() or list(classes.rglob("*.class")) != [compiled]:
        raise ValueError("Preset compilation did not produce exactly one class")
    entries[PRESET] = compiled.read_bytes()

    asm = [str(path) for path in map(Path, sdk.split(":"))
           if path.name.startswith(("asm-9.", "asm-tree-9."))]
    if len(asm) != 2:
        raise ValueError("Expected one ASM core and one tree JAR")
    patch_classes = output / "patch-classes"
    shutil.rmtree(patch_classes, ignore_errors=True)
    patch_classes.mkdir()
    run([javac, "-J-Xmx192m", "--release", "21", "-cp", ":".join(asm),
         "-d", str(patch_classes), str(HERE / "PatchSpyglass.java"),
         str(HERE / "PatchMusicReference.java")], output / "patch-compile.log")
    original = output / "roles-original.class"
    patched = output / "roles-spyglass.class"
    original.write_bytes(entries[ROLES])
    run([java, "-Xmx128m", "-cp", f"{patch_classes}:{':'.join(asm)}", "PatchSpyglass",
         str(original), str(patched)], output / "patch-run.log")
    entries[ROLES] = patched.read_bytes()
    old_audio = output / "scooter-audio-original.class"
    new_audio = output / "scooter-audio-relocated.class"
    old_audio.write_bytes(entries[AUDIO])
    run([java, "-Xmx128m", "-cp", f"{patch_classes}:{':'.join(asm)}", "PatchMusicReference",
         str(old_audio), str(new_audio)], output / "music-patch-run.log")
    entries[AUDIO] = new_audio.read_bytes()

    metadata = entries[META].decode()
    if metadata.count('version="3.0.12-bmc5"') != 6:
        raise ValueError("Unexpected 3.0.12 mod metadata")
    entries[META] = metadata.replace('version="3.0.12-bmc5"', 'version="3.0.13-bmc5"').encode()
    destination = output / "psychiatryk_roles-3.0.13-bmc5.jar"
    with zipfile.ZipFile(destination, "w", zipfile.ZIP_DEFLATED) as archive:
        for name, data in sorted(entries.items()):
            item = zipfile.ZipInfo(name, (2026, 10, 9, 8, 0, 0))
            item.compress_type = zipfile.ZIP_DEFLATED
            item.external_attr = 0o100644 << 16
            archive.writestr(item, data)
    with zipfile.ZipFile(destination) as archive:
        final = {name: archive.read(name) for name in archive.namelist()}
    if final != entries:
        raise ValueError("Candidate archive round-trip failed")
    with zipfile.ZipFile(base) as archive:
        before = {name: archive.read(name) for name in archive.namelist() if not name.endswith("/")}
    changed = {name for name in before if before[name] != final[name]}
    if changed != {PRESET, ROLES, AUDIO, META} or set(before) != set(final):
        raise ValueError(f"Unexpected JAR changes: {sorted(changed)}")
    old_music = b"pl/aridlin/psychiatrykroles/music/MusicAudibility"
    if any(old_music in data for name, data in final.items() if name.endswith(".class")):
        raise ValueError("A class still references the mixin-owned old music package")
    proof = {
        "base_sha256": BASE_SHA256,
        "candidate_sha256": sha(destination.read_bytes()),
        "candidate_bytes": destination.stat().st_size,
        "changed_entries": sorted(changed),
        "unchanged_entries": len(before) - len(changed),
        "preset_source_sha256": sha(SOURCE.read_bytes()),
        "patch_source_sha256": sha((HERE / "PatchSpyglass.java").read_bytes()),
        "music_patch_source_sha256": sha((HERE / "PatchMusicReference.java").read_bytes()),
        "deployed": False,
        "client_qa": False,
    }
    (output / "proof.json").write_text(json.dumps(proof, indent=2) + "\n")
    return proof


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base", type=Path, required=True)
    parser.add_argument("--sdk", type=Path, required=True)
    parser.add_argument("--output", type=Path, default=HERE / "build")
    args = parser.parse_args()
    print(json.dumps(build(args.base, args.sdk, args.output), indent=2))
