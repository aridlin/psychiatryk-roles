#!/usr/bin/env python3
"""Build a private exact 3.0.10 client-mod ZIP; never publish its third-party JARs."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import shutil
import tempfile
import zipfile
from pathlib import Path


ROLES_MANAGED = "psychiatryk_roles-3.0.0-bmc5.jar"
STAMP = (2026, 10, 9, 0, 0, 0)
MOD_ID = re.compile(rb'\bmodId\s*=\s*["\']psychiatryk_roles["\']')
DESCRIPTORS = ("META-INF/neoforge.mods.toml", "META-INF/mods.toml")


def file_sha(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        while chunk := stream.read(1024 * 1024):
            digest.update(chunk)
    return digest.hexdigest()


def parse_qa_hashes(path: Path) -> dict[str, str]:
    hashes: dict[str, str] = {}
    for line in path.read_text().splitlines():
        expected, name = line.split("  ", 1)
        if len(expected) != 64 or name in hashes or "/" in name or "\\" in name:
            raise ValueError("invalid or duplicate QA inventory entry")
        hashes[name] = expected
    if len(hashes) != 356:
        raise ValueError(f"QA inventory has {len(hashes)} entries, expected 356")
    return hashes


def available_memory_mib() -> int:
    for line in Path("/proc/meminfo").read_text().splitlines():
        if line.startswith("MemAvailable:"):
            return int(line.split()[1]) // 1024
    raise ValueError("MemAvailable missing from /proc/meminfo")


def declares_roles(path: Path, *, full_check: bool = False) -> bool:
    with zipfile.ZipFile(path) as archive:
        if full_check and archive.testzip() is not None:
            raise ValueError(f"corrupt mod JAR: {path.name}")
        names = set(archive.namelist())
        return any(MOD_ID.search(archive.read(name)) for name in DESCRIPTORS if name in names)


def zip_entry(archive: zipfile.ZipFile, name: str, source: Path) -> None:
    info = zipfile.ZipInfo(name, date_time=STAMP)
    info.compress_type = zipfile.ZIP_STORED  # JAR files are already compressed.
    info.external_attr = 0o100644 << 16
    with source.open("rb") as input_stream, archive.open(info, "w") as output_stream:
        shutil.copyfileobj(input_stream, output_stream, length=1024 * 1024)


def main() -> None:
    here = Path(__file__).resolve().parent
    work = here.parents[2].parent
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--profile-mods", type=Path, default=work / "client-qa-20261008/prism/instances/psychiatryk-bmc5-3010-qa/minecraft/mods")
    parser.add_argument("--qa-hashes", type=Path, default=work / "client-qa-20261008/qa-staged-mods.sha256")
    parser.add_argument("--roles-jar", type=Path, required=True)
    parser.add_argument("--roles-sha256", required=True)
    parser.add_argument("--output-dir", type=Path, default=here / "build/client-installer-3.0.10/private")
    parser.add_argument("--min-available-mib", type=int, default=5000)
    args = parser.parse_args()
    if available_memory_mib() < args.min_available_mib:
        raise RuntimeError("host memory below packaging floor; wait for the server smoke to exit")
    if not re.fullmatch(r"[0-9a-f]{64}", args.roles_sha256):
        raise ValueError("roles SHA-256 must be exactly 64 lowercase hex characters")
    if file_sha(args.roles_jar) != args.roles_sha256 or not declares_roles(args.roles_jar, full_check=True):
        raise ValueError("candidate Roles JAR is wrong or does not declare psychiatryk_roles")

    expected = parse_qa_hashes(args.qa_hashes)
    files = sorted(args.profile_mods.glob("*.jar"))
    if len(files) != 356 or any(path.is_symlink() for path in files):
        raise ValueError(f"profile must contain 356 regular, non-symlink JARs; found {len(files)}")
    roles = [path for path in files if path.name.startswith("psychiatryk_roles-")]
    if len(roles) != 1:
        raise ValueError(f"expected exactly one profile Roles JAR; found {len(roles)}")
    expected_nonroles = {name: value for name, value in expected.items() if not name.startswith("psychiatryk_roles-")}
    profile_nonroles = {path.name: path for path in files if path not in roles}
    if set(profile_nonroles) != set(expected_nonroles):
        raise ValueError("profile non-Roles filenames differ from verified QA inventory")
    for name, path in profile_nonroles.items():
        if file_sha(path) != expected_nonroles[name]:
            raise ValueError(f"profile mod changed since QA inventory: {name}")
        if declares_roles(path):
            raise ValueError(f"duplicate psychiatryk_roles mod ID declared by {name}")
    # Explicitly normalize the managed filename. AutoModpack serves this same
    # path; leaving psychiatryk_roles-3.0.10-bmc5.jar beside it is a duplicate.
    profile_nonroles[ROLES_MANAGED] = args.roles_jar
    if len(profile_nonroles) != 356:
        raise ValueError("final inventory has a missing or duplicate Roles mod ID")
    inventory = {
        f"mods/{name}": {"bytes": path.stat().st_size, "sha256": file_sha(path)}
        for name, path in sorted(profile_nonroles.items())
    }
    if inventory[f"mods/{ROLES_MANAGED}"]["sha256"] != args.roles_sha256:
        raise ValueError("normalized Roles entry does not match final candidate")
    manifest = {
        "status": "private-local-staging-only",
        "redistribution": "Do not publish; many third-party JARs have restrictive licenses.",
        "minecraft": "1.21.1",
        "neoforge": "21.1.252",
        "rolesSha256": args.roles_sha256,
        "jarCount": len(inventory),
        "files": inventory,
        "limitation": "Mods only. The QA profile is missing 33 non-mod feed files and differs in seven configs; this is not a complete offline pack or a proven client launch.",
    }
    manifest_data = (json.dumps(manifest, indent=2, ensure_ascii=False) + "\n").encode()
    checksums = "".join(
        f"{details['sha256']}  {name}\n" for name, details in inventory.items()
    ).encode()
    note = (
        "PRIVATE Psychiatryk BMC5 3.0.10 client-mod inventory.\n"
        "Contains third-party JARs; do not upload to info.goplanska.pl or Modrinth.\n"
        "The public Prism/CurseForge installers reference upstream download files.\n"
        "Minecraft 1.21.1 / NeoForge 21.1.252 / Java 21.\n"
        "This mods-only ZIP does not prove a launch or player join and omits some\n"
        "non-mod AutoModpack content. See manifest.json for exact limitations.\n"
    ).encode()
    args.output_dir.mkdir(parents=True, exist_ok=True)
    required_space = sum(path.stat().st_size for path in profile_nonroles.values()) + 512 * 1024 * 1024
    if shutil.disk_usage(args.output_dir).free < required_space:
        raise RuntimeError("insufficient free disk space for private mod ZIP plus headroom")
    destination = args.output_dir / "psychiatryk-bmc5-v53-3.0.10-client-mods-PRIVATE.zip"
    with tempfile.NamedTemporaryFile(prefix=".client-mods-", suffix=".zip", dir=args.output_dir, delete=False) as handle:
        temporary = Path(handle.name)
    try:
        with zipfile.ZipFile(temporary, "w", allowZip64=True) as archive:
            archive.writestr("README-PRIVATE.txt", note, compress_type=zipfile.ZIP_DEFLATED)
            archive.writestr("manifest.json", manifest_data, compress_type=zipfile.ZIP_DEFLATED)
            archive.writestr("SHA256SUMS.txt", checksums, compress_type=zipfile.ZIP_DEFLATED)
            for name, path in sorted(profile_nonroles.items()):
                zip_entry(archive, f"mods/{name}", path)
        with zipfile.ZipFile(temporary) as archive:
            names = archive.namelist()
            if archive.testzip() is not None or len(names) != len(set(names)) or len(names) != 359:
                raise ValueError("private ZIP has corrupt, missing, or duplicate entries")
            if sum(name.startswith("mods/psychiatryk_roles-") for name in names) != 1:
                raise ValueError("private ZIP contains duplicate Roles JAR filenames")
            for name, details in inventory.items():
                with archive.open(name) as stream:
                    digest = hashlib.sha256()
                    while chunk := stream.read(1024 * 1024):
                        digest.update(chunk)
                if digest.hexdigest() != details["sha256"]:
                    raise ValueError(f"private ZIP readback differs: {name}")
        os.replace(temporary, destination)
    finally:
        temporary.unlink(missing_ok=True)
    proof = {
        "status": "private-local-staging-only",
        "archive": destination.name,
        "bytes": destination.stat().st_size,
        "sha256": file_sha(destination),
        "jarCount": len(inventory),
        "rolesSha256": args.roles_sha256,
        "publicUploadAllowed": False,
    }
    (args.output_dir / "client-mods-proof.json").write_text(json.dumps(proof, indent=2) + "\n")
    print(json.dumps(proof, indent=2))


if __name__ == "__main__":
    main()
