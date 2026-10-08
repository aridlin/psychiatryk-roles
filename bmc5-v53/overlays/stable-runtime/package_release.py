#!/usr/bin/env python3
"""Build a hash-pinned, NON-DEPLOYED 3.0.10 server/client payload.

This archive is deliberately not named bmc5-patch.zip and contains no
bmc5-patch.properties activation flag. The existing PatchActivation bootstrap
cannot admit the new KubeJS and Rhino paths; deployment needs a separately
qualified stopped-server transaction.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import tempfile
import zipfile
from pathlib import Path


BASELINE_SHA = "a513a1e338f0651648dfa32ecf9f3049daab312c2304183083337ecbc43572de"
CANDIDATE_SHA = "3d4842404a38b205a3ca0541ae6ba5a4b4423ef0ecc46fcc094baec5f7970964"
KUBEJS_SHA = "df9a8458b1f83fed06ba54684977911795a5153a462b81a610895f5b1f2f0cbf"
RHINO_SHA = "e0e9b0e78edd380440266c0f4ea8d489dac851ef075a4566a66a6dae2f7bbb66"
ROLES_NAME = "psychiatryk_roles-3.0.0-bmc5.jar"  # Managed filename is intentionally stable.
KUBEJS_NAME = "kubejs-neoforge-2101.7.2-build.377.jar"
RHINO_NAME = "rhino-2101.2.7-build.85.jar"
ZIP_TIMESTAMP = (2026, 10, 9, 0, 0, 0)
NEW_FEATURES = (
    "stable-runtime-protocol1",
    "server-defined-runtime-menus-and-controls",
    "server-defined-runtime-item-and-block-variants",
    "server-defined-hud-scenes-and-cached-assets",
    "reloadable-grapple-policy-and-event-hooks",
    "client-join-asset-progress",
)


def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def read_pinned(path: Path, expected: str) -> bytes:
    data = path.read_bytes()
    actual = digest(data)
    if actual != expected:
        raise ValueError(f"{path.name}: expected SHA-256 {expected}, found {actual}")
    return data


def zip_member_bytes(archive: zipfile.ZipFile, name: str) -> bytes:
    members = archive.namelist()
    if members.count(name) != 1:
        raise ValueError(f"baseline has {members.count(name)} copies of {name}")
    return archive.read(name)


def release_metadata(queued_zip: Path) -> bytes:
    with zipfile.ZipFile(queued_zip) as archive:
        original = zip_member_bytes(archive, "goplanska-release.json")
        paired = zip_member_bytes(
            archive, "automodpack/host-modpack/main/goplanska-release.json"
        )
        baseline = zip_member_bytes(archive, f"mods/{ROLES_NAME}")
        hosted_baseline = zip_member_bytes(
            archive, f"automodpack/host-modpack/main/mods/{ROLES_NAME}"
        )
        if original != paired or digest(baseline) != BASELINE_SHA or baseline != hosted_baseline:
            raise ValueError("queued 3.0.9 baseline server/client bytes do not match")
        metadata = json.loads(original)
        if (
            metadata.get("release") != "bmc5-v53-roles3.0.9"
            or metadata.get("addonSha256") != BASELINE_SHA
            or metadata.get("minecraft") != "1.21.1"
        ):
            raise ValueError("queued archive is not the pinned 3.0.9 release")
        if not isinstance(metadata.get("features"), list):
            raise ValueError("queued release has no feature list")
    metadata["release"] = "bmc5-v53-roles3.0.10"
    metadata["addonSha256"] = CANDIDATE_SHA
    metadata["neoforge"] = "21.1.252"
    metadata["java"] = 21
    metadata["clientUpdateRequiredFrom309"] = True
    metadata["runtimeProtocol"] = 1
    metadata["features"] = list(dict.fromkeys([*metadata["features"], *NEW_FEATURES]))
    return (json.dumps(metadata, indent=2, ensure_ascii=False) + "\n").encode("utf-8")


def zipped_payload(payload: dict[str, bytes], destination: Path) -> None:
    content_hashes = {name: digest(data) for name, data in sorted(payload.items())}
    properties = "".join(f"{name}={value}\n" for name, value in content_hashes.items())
    with zipfile.ZipFile(destination, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=6) as archive:
        for name, data in sorted({**payload, "manifest.properties": properties.encode()}.items()):
            entry = zipfile.ZipInfo(name, date_time=ZIP_TIMESTAMP)
            entry.compress_type = zipfile.ZIP_DEFLATED
            entry.external_attr = 0o100644 << 16
            archive.writestr(entry, data, compress_type=zipfile.ZIP_DEFLATED, compresslevel=6)
    with zipfile.ZipFile(destination) as archive:
        expected_names = set(payload) | {"manifest.properties"}
        if set(archive.namelist()) != expected_names or archive.testzip() is not None:
            raise ValueError("built ZIP has a missing, unexpected, duplicate, or corrupt member")
        for name, expected_hash in content_hashes.items():
            if digest(archive.read(name)) != expected_hash:
                raise ValueError(f"built ZIP failed member hash: {name}")


def notes(archive_hash: str, members: dict[str, bytes]) -> str:
    lines = [
        "# Psychiatryk 3.0.10 local staging bundle",
        "",
        "**Not deployed or qualified by packaging.** Do not upload this archive as",
        "`bmc5-patch.zip`: the current startup bootstrap accepts only the four",
        "3.0.9 payload paths and will reject KubeJS/Rhino. No activation flag is",
        "included. Use a separate, verified stopped-server transaction.",
        "",
        f"- Archive SHA-256: `{archive_hash}`",
        f"- Required live 3.0.9 roles baseline SHA-256: `{BASELINE_SHA}`",
        f"- New paired roles JAR SHA-256: `{CANDIDATE_SHA}`",
        f"- Paired KubeJS build 377 SHA-256: `{KUBEJS_SHA}`",
        f"- Paired Rhino build 85 SHA-256: `{RHINO_SHA}`",
        "",
        "The server and AutoModpack hosted copies of each JAR and release metadata",
        "must match exactly. KubeJS and Rhino are included on both sides because",
        "the selected KubeJS build declares Rhino as a required BOTH-side dependency.",
        "The 3.0.10 roles JAR adds a native runtime block/item; clients on 3.0.9",
        "must receive the matching one-time update before their next join.",
        "",
        "Before installation, wait for the separately scheduled 3.0.9 patch to",
        "finish; verify the live roles JAR hash above. Stop the server. Back up and",
        "hash the current world, player data, server/hosted roles JARs, release",
        "metadata, AutoModpack feed, runtime config/assets/scripts, and private",
        "runtime signing key. Preserve the signing key unchanged. Check disk space",
        "before staging the eight payload files. Replace only the listed paths as",
        "one rollback-capable transaction. Start the server, verify the exact",
        "JAR/mod inventory and AutoModpack feed, then prove a real client join,",
        "rejoin, menu render, and asset cache hit/miss. Do not equate a server",
        "status ping or this archive integrity check with those live proofs.",
        "",
        "If verification fails, stop the server and restore the saved server and",
        "hosted roles JARs, release metadata, AutoModpack feed, and any changed",
        "runtime data. Remove KubeJS/Rhino only if they were absent in the saved",
        "baseline; otherwise restore their saved bytes. Restart and verify the",
        "previous release and a real join. A client that already installed 3.0.10",
        "may need its matching old JAR restored by AutoModpack for 3.0.9.",
        "",
        "Payload entries:",
        "",
    ]
    lines.extend(f"- `{name}` — {len(data)} bytes; SHA-256 `{digest(data)}`" for name, data in sorted(members.items()))
    return "\n".join(lines) + "\n"


def main() -> None:
    repo = Path(__file__).resolve().parents[3]
    private = repo.parent / "migration/private_release/workstations-20261008"
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--queued-zip", type=Path, default=private / "queued.zip")
    parser.add_argument("--candidate", type=Path, default=Path(__file__).resolve().parent / "build/psychiatryk_roles-3.0.10-bmc5.jar")
    parser.add_argument("--kubejs", type=Path, default=private / f"kubejs-stage/mods/{KUBEJS_NAME}")
    parser.add_argument("--rhino", type=Path, default=private / f"kubejs-stage/mods/{RHINO_NAME}")
    parser.add_argument("--output-dir", type=Path, default=Path(__file__).resolve().parent / "build/release-3.0.10")
    args = parser.parse_args()

    metadata = release_metadata(args.queued_zip)
    roles = read_pinned(args.candidate, CANDIDATE_SHA)
    kubejs = read_pinned(args.kubejs, KUBEJS_SHA)
    rhino = read_pinned(args.rhino, RHINO_SHA)
    payload: dict[str, bytes] = {
        f"mods/{ROLES_NAME}": roles,
        f"mods/{KUBEJS_NAME}": kubejs,
        f"mods/{RHINO_NAME}": rhino,
        "goplanska-release.json": metadata,
    }
    for name, data in list(payload.items()):
        payload[f"automodpack/host-modpack/main/{name}"] = data
    args.output_dir.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(prefix=".psychiatryk-3.0.10-", suffix=".zip", dir=args.output_dir, delete=False) as temp:
        temporary = Path(temp.name)
    try:
        zipped_payload(payload, temporary)
        archive_hash = digest(temporary.read_bytes())
        destination = args.output_dir / "psychiatryk-3.0.10-payload.zip"
        os.replace(temporary, destination)
    finally:
        temporary.unlink(missing_ok=True)
    inventory = {
        "status": "local-staging-only",
        "qualified": False,
        "deployed": False,
        "bootstrapCompatible": False,
        "archive": destination.name,
        "archiveSha256": archive_hash,
        "queued309BaselineSha256": BASELINE_SHA,
        "candidateRolesSha256": CANDIDATE_SHA,
        "members": {
            name: {"sha256": digest(data), "bytes": len(data)}
            for name, data in sorted(payload.items())
        },
    }
    (args.output_dir / "release-manifest.json").write_text(
        json.dumps(inventory, indent=2) + "\n", encoding="utf-8"
    )
    (args.output_dir / "INSTALL-AND-ROLLBACK.md").write_text(
        notes(archive_hash, payload), encoding="utf-8"
    )
    print(f"Staged only: {destination}")
    print(f"SHA-256: {archive_hash}")
    print(f"Members: {len(payload)} paired server/hosted files; qualification pending")


if __name__ == "__main__":
    main()
