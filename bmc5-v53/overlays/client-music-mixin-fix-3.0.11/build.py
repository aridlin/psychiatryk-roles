#!/usr/bin/env python3
"""Build the 3.0.11 client-startup hotfix from the exact 3.0.10 JAR.

Only four source classes, three removed helper classes, and mod versions change.
The original 3.0.10 artifact remains an immutable, hash-pinned input.
"""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import zipfile


BASE_SHA256 = "eb4b1685205ba1ac3432b1708572d829e9004193ca8857b8b05798bc48f6ce12"
MIXIN_PACKAGE = "pl/aridlin/psychiatrykroles/music/"
AUDIO_PACKAGE = "pl/aridlin/psychiatrykroles/audio/"
RELOCATED = ("BackgroundMusicPauseState", "MusicAudibility", "VanillaJukeboxAudio")


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def build(base: Path, sdk: Path, output: Path) -> dict:
    root = Path(__file__).resolve().parent
    source = root / "src"
    sources = sorted(source.rglob("*.java"))
    if len(sources) != 4:
        raise ValueError("Expected exactly three relocated helpers and one replacement mixin")
    base_bytes = base.read_bytes()
    if sha256(base_bytes) != BASE_SHA256:
        raise ValueError("Wrong 3.0.10 baseline JAR")
    classpath = sdk.read_text().strip()
    if not classpath:
        raise ValueError("Empty Minecraft SDK classpath")
    output.mkdir(parents=True, exist_ok=True)
    classes = output / "classes"
    shutil.rmtree(classes, ignore_errors=True)
    classes.mkdir()
    compiler = Path("/usr/lib/jvm/java-21-openjdk/bin/javac")
    if not compiler.is_file():
        compiler = Path("javac")
    compile_result = subprocess.run(
        [str(compiler), "-J-Xmx384m", "--release", "21", "-proc:none",
         "-cp", str(base.resolve()) + ":" + classpath,
         "-d", str(classes), *map(str, sources)],
        capture_output=True, text=True, check=False,
    )
    (output / "compile.log").write_text(compile_result.stdout + compile_result.stderr)
    if compile_result.returncode:
        raise RuntimeError("Hotfix Java compile failed; see compile.log")
    with zipfile.ZipFile(base) as archive:
        entries = {name: archive.read(name) for name in archive.namelist() if not name.endswith("/")}
    before = dict(entries)
    removed = []
    for helper in RELOCATED:
        old = MIXIN_PACKAGE + helper + ".class"
        if old not in entries:
            raise ValueError("Missing expected old helper: " + old)
        removed.append(old)
        del entries[old]
    replacements = {}
    for file in sorted(classes.rglob("*.class")):
        name = file.relative_to(classes).as_posix()
        if not (name == MIXIN_PACKAGE + "MusicBackgroundMixin.class"
                or name in {AUDIO_PACKAGE + helper + ".class" for helper in RELOCATED}):
            raise ValueError("Unexpected compiled class: " + name)
        replacements[name] = file.read_bytes()
        entries[name] = replacements[name]
    if len(replacements) != 4:
        raise ValueError("Missing compiled replacement classes")
    metadata_name = "META-INF/neoforge.mods.toml"
    metadata = entries[metadata_name].decode("utf-8")
    if metadata.count('version="3.0.10-bmc5"') != 6:
        raise ValueError("Unexpected 3.0.10 mod metadata")
    entries[metadata_name] = metadata.replace('version="3.0.10-bmc5"',
                                              'version="3.0.11-bmc5"').encode("utf-8")
    if {name for name in before if before[name] != entries.get(name)} != (set(removed) | {MIXIN_PACKAGE + "MusicBackgroundMixin.class", metadata_name}):
        raise ValueError("Unexpected baseline content change")
    if set(entries) - set(before) != {AUDIO_PACKAGE + helper + ".class" for helper in RELOCATED}:
        raise ValueError("Unexpected added archive entries")
    destination = output / "psychiatryk_roles-3.0.11-bmc5.jar"
    with zipfile.ZipFile(destination, "w", zipfile.ZIP_DEFLATED) as archive:
        for name, data in sorted(entries.items()):
            info = zipfile.ZipInfo(name, (2026, 10, 9, 8, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            archive.writestr(info, data)
    proof = {
        "base_sha256": BASE_SHA256,
        "candidate_sha256": sha256(destination.read_bytes()),
        "candidate_bytes": destination.stat().st_size,
        "removed_helpers": removed,
        "added_audio_classes": sorted(set(entries) - set(before)),
        "replaced_mixin": MIXIN_PACKAGE + "MusicBackgroundMixin.class",
        "metadata_version": "3.0.11-bmc5",
        "source_files": {str(path.relative_to(root)): sha256(path.read_bytes()) for path in sources},
        "compile_21_success": True,
        "game_launched": False,
    }
    (output / "build-proof.json").write_text(json.dumps(proof, indent=2) + "\n")
    return proof


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base", required=True, type=Path)
    parser.add_argument("--sdk", required=True, type=Path)
    parser.add_argument("--output", type=Path, default=Path(__file__).resolve().parent / "build")
    args = parser.parse_args()
    print(json.dumps(build(args.base, args.sdk, args.output)))
