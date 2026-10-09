#!/usr/bin/env python3
"""Stage local 3.0.10 Prism, CurseForge and manual-addon installer archives.

The live 3.0.8 installers are tiny AutoModpack bootstraps, not offline BMC5
archives. This script preserves that model: first-party Roles is embedded,
its first-launch dependencies resolve from official Modrinth/CurseForge file
records, and AutoModpack gets the remaining server pack after connecting. Nothing is
published or installed by this script.
"""

from __future__ import annotations

import argparse
import hashlib
import io
import json
import os
import tempfile
import zipfile
from pathlib import Path

import package_release


BASE_MR_SHA = "45efc83d2f984876f7459d6ffacd01c06d0fe1273903796fe98ecac49494ba6f"
BASE_CF_SHA = "8f10de12faeafc5cb35688aff811fda87d5defbcdeb500db329247fd9ef428d9"
BASE_ADDON_SHA = "772b8558535290819d9227abdd34b324eae77f8bc0ed86b5b9e2e06bd462b6e7"
AUTOMODPACK_SHA512 = "cc07b19c221d9f3b1a9bb7aaa2ddc65b535f53f0b740026a086319ceabee023c657b66cf3df2c7d8cfe3f4563a3eed84bfb990437cb19a11e7f1c27fef27e3f2"
ZIP_TIMESTAMP = (2026, 10, 9, 0, 0, 0)
ROLES = "psychiatryk_roles-3.0.0-bmc5.jar"
KUBEJS = {
    "filename": package_release.KUBEJS_NAME,
    "sha256": package_release.KUBEJS_SHA,
    "sha512": "fcdbee8790f8f39c106ab4bf606808c9c5bf0f13a41e216207a6fe4d15bf4a5722ce57078781e3a1823e7fdd2928e8e042d104d8ae255a36db8f12a9f46d8089",
    "sha1": "7313fcd99318834c642ee9f3a05ed7fd7bcb6ef4",
    "size": 2294111,
    "modrinth_project": "umyGl7zF",
    "modrinth_version": "THIGFPwf",
    "curseforge_project": 238086,
    "curseforge_file": 8843626,
}
RHINO = {
    "filename": package_release.RHINO_NAME,
    "sha256": package_release.RHINO_SHA,
    "sha512": "0a6c7f4410281aeb03b815ec03487a8e80a4d3dae013d81282e447bf9c08144ef57318af614ea6d40b7d9a8c25031c557890ec792a5895a82467c1643116f693",
    "sha1": "3c8d34c7efe9e42fc90506acbf9fc842d36f9a42",
    "size": 882075,
    "modrinth_project": "sk9knFPE",
    "modrinth_version": "cQ4POTah",
    "curseforge_project": 416294,
    "curseforge_file": 8218748,
}
ACCESSORIES = {
    "filename": "accessories-neoforge-1.1.0-beta.53+1.21.1.jar",
    "sha256": "10017a3da78ea63e9ece27a1ca32f8cf490362f348778cf8cb759e7282f3beb0",
    "sha512": "baafa9a5e48c17c243d45b6260095ffef2ad00e4e970aafc5b1ca9ab5f4a542b18b0fb35d4584318791edbaf00a3c44806f62062513d991383192aca4df27a07",
    "sha1": "77d75c2e13cfdf56a45cdd29806c1c97c3d250fc",
    "size": 1078697,
    "modrinth_project": "jtmvUHXj",
    "modrinth_version": "Fb55Fgjz",
    "curseforge_project": 938917,
    "curseforge_file": 7583320,
}
OWO = {
    "filename": "owo-lib-neoforge-0.12.15.5-beta.1+1.21.jar",
    "sha256": "de6ed336bd80154b7241a7b3276694befc1c94550add8bcdfe7f82e5172fd13d",
    "sha512": "4de5c5d52139244b8c5260d641087664d992624b822599a32e03c08eb133be854a2f413667dbca1e55772445b04a70210c17b3bc13e3c88e425e7d928104b9fa",
    "sha1": "48dda11a6710591cf162bdbedf982ea21dd1f2ed",
    "size": 1221583,
    "modrinth_project": "ccKDOlHs",
    "modrinth_version": "NMCHU6DZ",
    "curseforge_project": 532610,
    "curseforge_file": 6785734,
}


def sha(data: bytes, algorithm: str = "sha256") -> str:
    h = hashlib.new(algorithm)
    h.update(data)
    return h.hexdigest()


def source_files(path: Path, expected_sha: str) -> dict[str, bytes]:
    raw = path.read_bytes()
    if sha(raw) != expected_sha:
        raise ValueError(f"{path.name}: unexpected base archive SHA-256")
    with zipfile.ZipFile(io.BytesIO(raw)) as archive:
        names = archive.namelist()
        if len(names) != len(set(names)) or archive.testzip() is not None:
            raise ValueError(f"{path.name}: duplicate or corrupt ZIP entries")
        return {name: archive.read(name) for name in names}


def verify_dependency(path: Path, spec: dict[str, object]) -> None:
    data = path.read_bytes()
    for algorithm in ("sha256", "sha512", "sha1"):
        if sha(data, algorithm) != spec[algorithm]:
            raise ValueError(f"{path.name}: {algorithm} differs from pinned official file")
    if len(data) != spec["size"] or path.name != spec["filename"]:
        raise ValueError(f"{path.name}: dependency filename/size differs")


def modrinth_entry(spec: dict[str, object]) -> dict[str, object]:
    return {
        "path": f"mods/{spec['filename']}",
        "hashes": {"sha1": spec["sha1"], "sha512": spec["sha512"]},
        "env": {"client": "required", "server": "required"},
        "downloads": [
            f"https://cdn.modrinth.com/data/{spec['modrinth_project']}/versions/{spec['modrinth_version']}/{spec['filename']}"
        ],
        "fileSize": spec["size"],
    }


def readme(roles_sha: str) -> bytes:
    return f"""# Goplanska / Psychiatryk 3.0.10 — installer

## Polski

1. Zaimportuj ten plik jako **nową instancję**: Prism → Dodaj instancję →
   Importuj → plik `.mrpack`; CurseForge → Importuj → plik `.zip`.
2. Użyj Java 21 i konta Minecraft. Paczka ustawia Minecraft 1.21.1,
   **NeoForge 21.1.252**, AutoModpack 4.0.6, Psychiatryk 3.0.10 oraz
   KubeJS 2101.7.2-build.377 z Rhino 2101.2.7-build.85 oraz
   Accessories 1.1.0-beta.53 z wymaganym oωo 0.12.15.5-beta.1.
3. Połącz z **goplanska.pl**. Porównaj odcisk AutoModpack:
   `e47e202810281086f31e37c74ec121d611b31e50adb6a4a68131fbabe2f0cee6`.
   Zaczekaj, aż AutoModpack pobierze resztę Better MC5 v53 i dokończ wymagany
   restart gry. W przyszłości używaj tej samej instancji.

To instalator sieciowy, **nie pełna paczka offline**. Własny mod ma SHA-256
`{roles_sha}` i jego nazwa pliku celowo pozostaje `{ROLES}`.
Zachowaj poprzednią instancję jako kopię. Nie mieszaj ze starym zestawem S23
ani osobnymi modami Portable Scooters/Chams. Plik `servers.dat` zapisuje
tylko nazwę oraz adres serwera; instalator nie ustawia zgody na resource pack.
Oficjalna baza: https://www.curseforge.com/minecraft/modpacks/better-mc-neoforge-bmc5/files/8835241

## English

Import as a **new instance** in Prism (.mrpack) or CurseForge (.zip). Use Java
21 and your Minecraft account. The installer pins Minecraft 1.21.1,
**NeoForge 21.1.252**, AutoModpack 4.0.6, matching Psychiatryk 3.0.10,
KubeJS build 377, Rhino build 85, Accessories beta.53 and oωo beta.1. Join **goplanska.pl**, confirm the
AutoModpack fingerprint above, let it fetch the remaining Better MC5 v53
files, and complete any requested game relaunch. Reuse the same instance for
later updates and keep the previous instance for rollback.

This is a network bootstrap, **not a complete offline BMC5 pack**. The
first-party Roles JAR is embedded with its code and asset notices. KubeJS,
Rhino, Accessories and oωo are fetched from their official project file records. Upstream Better
MC5 content remains with its original distributors. No world, player data,
server secrets or forced resource pack are included.
""".encode("utf-8")


def addon_readme(roles_sha: str) -> bytes:
    return f"""# Psychiatryk 3.0.10 — ręczny dodatek / manual addon

Ten ZIP **nie jest instalatorem paczki**. Zaimportuj najpierw oficjalny Better
MC [NEOFORGE] BMC5 v53 jako nową instancję, a w Prism zmień NeoForge na
**21.1.252**. Zamknij grę i wypakuj zawartość tego ZIP-a do folderu gry,
zachowując ścieżki `mods/` i `config/`. W `mods/` ma pozostać tylko jedna
wersja Psychiatryk Roles: `{ROLES}` (SHA-256 `{roles_sha}`).

Zainstaluj AutoModpack 4.0.6 dla NeoForge 1.21.1. Przed dołączeniem dodaj
KubeJS 2101.7.2-build.377 i Rhino 2101.2.7-build.85 z oficjalnych plików
wskazanych w `DEPENDENCIES-3.0.10.json`, chyba że pobierze je już launcher.
Dołącz przez `goplanska.pl`, sprawdź odcisk AutoModpack w witrynie i pozwól
mu pobrać resztę zestawu serwera. Dokończ wymagany restart gry.

This ZIP is a **manual overlay, not an importable full pack**. Import the
official Better MC5 v53 as a new instance, change its NeoForge version to
**21.1.252**, close Minecraft, then extract this ZIP into the instance's game
folder while preserving `mods/` and `config/` paths. Keep exactly one Roles
JAR under the stable filename above. Install AutoModpack 4.0.6 and the pinned
KubeJS/Rhino versions from their official files in the dependency manifest,
then join `goplanska.pl` to fetch the remaining server pack. This archive
contains only the first-party JAR, selected configs, recipes and metadata;
it is not a redistribution of Better MC5 or a complete offline pack.
""".encode("utf-8")


def changelog() -> bytes:
    return """# Psychiatryk 3.0.10

- One matching client update installs the generic runtime item/block,
  renderers and the versioned server/client protocol.
- This alternate candidate adds a fixed custom item renderer whose bounded
  variant artwork is preloaded and can hot-update from the server.
- The server can subsequently send bounded, cached GUI, HUD, Peeb animation,
  scooter visual and decorative-block data without another client update.
- Server-side event logic and gameplay policies can change through validated
  runtime data or trusted KubeJS scripts; native registry IDs and new renderer
  implementations still need a client release.
- The join screen reports cache verification, transfers and feature readiness.
- Psychiatryk-owned screens keep a sharp, unblurred background.

This archive alone is packaging evidence. Actual connected-client rendering,
cache-hit, and gameplay proof must be recorded separately.
""".encode("utf-8")


def json_bytes(data: object) -> bytes:
    return (json.dumps(data, indent=2, ensure_ascii=False) + "\n").encode("utf-8")


def reseal(entries: dict[str, bytes], path: Path) -> str:
    path.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(prefix=".client-installer-", suffix=".zip", dir=path.parent, delete=False) as handle:
        temporary = Path(handle.name)
    try:
        with zipfile.ZipFile(temporary, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=6) as archive:
            for name, data in sorted(entries.items()):
                info = zipfile.ZipInfo(name, date_time=ZIP_TIMESTAMP)
                info.compress_type = zipfile.ZIP_DEFLATED
                info.external_attr = 0o100644 << 16
                archive.writestr(info, data, compress_type=zipfile.ZIP_DEFLATED, compresslevel=6)
        with zipfile.ZipFile(temporary) as archive:
            if archive.testzip() is not None or len(archive.namelist()) != len(entries):
                raise ValueError(f"{path.name}: ZIP write verification failed")
            if any(archive.read(name) != data for name, data in entries.items()):
                raise ValueError(f"{path.name}: ZIP readback differs")
        os.replace(temporary, path)
    finally:
        temporary.unlink(missing_ok=True)
    return sha(path.read_bytes())


def main() -> None:
    here = Path(__file__).resolve().parent
    repo = here.parents[2]
    private = repo.parent / "migration/private_release/workstations-20261008"
    base = here / "build/client-installer-3.0.10"
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--roles-jar", type=Path, required=True)
    parser.add_argument("--roles-sha256", required=True)
    parser.add_argument("--base-dir", type=Path, default=base)
    parser.add_argument("--output-dir", type=Path, default=base / "item-candidate")
    parser.add_argument("--queued-zip", type=Path, default=private / "queued.zip")
    parser.add_argument("--kubejs", type=Path, default=private / f"kubejs-stage/mods/{package_release.KUBEJS_NAME}")
    parser.add_argument("--rhino", type=Path, default=private / f"kubejs-stage/mods/{package_release.RHINO_NAME}")
    parser.add_argument("--accessories", type=Path, default=private / f"live-mod-qa/mods/{ACCESSORIES['filename']}")
    parser.add_argument("--owo", type=Path, default=private / f"live-mod-qa/mods/{OWO['filename']}")
    args = parser.parse_args()
    if len(args.roles_sha256) != 64 or any(c not in "0123456789abcdef" for c in args.roles_sha256):
        raise ValueError("roles SHA-256 must be a lowercase 64-character hexadecimal string")
    roles = args.roles_jar.read_bytes()
    if sha(roles) != args.roles_sha256:
        raise ValueError("Roles JAR differs from supplied SHA-256")
    with zipfile.ZipFile(io.BytesIO(roles)) as archive:
        if archive.testzip() is not None:
            raise ValueError("Roles JAR is corrupt")
        descriptor = archive.read("META-INF/neoforge.mods.toml").decode()
        if 'version="3.0.10-bmc5"' not in descriptor or 'modId="psychiatryk_roles"' not in descriptor:
            raise ValueError("Roles JAR is not the 3.0.10 client mod")
        for notice in ("LICENSE-CODE-GPL-3.0.txt", "LICENSE-KUKIRIN-MODEL.txt", "NOTICE.txt"):
            if notice not in archive.namelist():
                raise ValueError(f"Roles JAR lacks {notice}")
    verify_dependency(args.kubejs, KUBEJS)
    verify_dependency(args.rhino, RHINO)
    verify_dependency(args.accessories, ACCESSORIES)
    verify_dependency(args.owo, OWO)

    mr = source_files(args.base_dir / "base-live-installer.mrpack", BASE_MR_SHA)
    cf = source_files(args.base_dir / "base-live-curseforge.zip", BASE_CF_SHA)
    addon = source_files(args.base_dir / "base-live-addon.zip", BASE_ADDON_SHA)
    if set(mr) != ((set(cf) - {"manifest.json"}) | {"modrinth.index.json"}):
        raise ValueError("live Prism/CurseForge installer overrides differ")
    for name in set(mr) & set(cf):
        if mr[name] != cf[name]:
            raise ValueError(f"live installer override differs: {name}")
    if mr["overrides/servers.dat"] != cf["overrides/servers.dat"]:
        raise ValueError("server address presets differ")
    index = json.loads(mr["modrinth.index.json"])
    curseforge = json.loads(cf["manifest.json"])
    if index["dependencies"] != {"minecraft": "1.21.1", "neoforge": "21.1.250"}:
        raise ValueError("unexpected live Modrinth loader")
    if curseforge["minecraft"]["modLoaders"] != [{"id": "neoforge-21.1.250", "primary": True}]:
        raise ValueError("unexpected live CurseForge loader")
    if len(index["files"]) != 1 or index["files"][0]["hashes"]["sha512"] != AUTOMODPACK_SHA512:
        raise ValueError("unexpected live AutoModpack reference")
    if curseforge["files"] != [{"projectID": 639211, "fileID": 8606541, "required": True}]:
        raise ValueError("unexpected live CurseForge AutoModpack reference")

    release = json.loads(package_release.release_metadata(args.queued_zip))
    release["addonSha256"] = args.roles_sha256
    release_bytes = json_bytes(release)
    download_release = json.loads(mr["overrides/GOPLANSKA-DOWNLOAD-RELEASE.json"])
    download_release.update({
        "release": "bmc5-v53-roles3.0.10",
        "addonSha256": args.roles_sha256,
        "neoforge": "21.1.252",
        "clientUpdateRequiredFrom309": True,
        "notes": "Installer embeds matching Psychiatryk Roles; official launcher references resolve KubeJS/Rhino/Accessories/oωo. AutoModpack downloads remaining BMC5 server files on first connection.",
    })
    dependency_manifest = {
        "purpose": "official upstream launcher file references, not redistributed JARs",
        "minecraft": "1.21.1",
        "neoforge": "21.1.252",
        "files": [KUBEJS, RHINO],
    }
    guide = readme(args.roles_sha256)
    changes = changelog()
    shared = {
        "README.md": guide,
        "overrides/GOPLANSKA-INSTALL.md": guide,
        "overrides/PLAY-3.0.10.md": guide,
        "overrides/CHANGELOG-3.0.10.md": changes,
        "overrides/goplanska-release.json": release_bytes,
        "overrides/GOPLANSKA-DOWNLOAD-RELEASE.json": json_bytes(download_release),
        "overrides/DEPENDENCIES-3.0.10.json": json_bytes(dependency_manifest),
        f"overrides/mods/{ROLES}": roles,
    }
    for name in list(mr):
        if name in ("overrides/PLAY-3.0.8.md", "overrides/CHANGELOG-3.0.8.md"):
            mr.pop(name)
    for name in list(cf):
        if name in ("overrides/PLAY-3.0.8.md", "overrides/CHANGELOG-3.0.8.md"):
            cf.pop(name)
    mr.update(shared)
    cf.update(shared)
    index.update({
        "versionId": "bmc5-v53-roles-3.0.10-installer",
        "name": "Goplanska — Better MC5 v53 / Roles 3.0.10",
        "summary": "NeoForge 21.1.252 first-run installer with Roles and required libraries; AutoModpack fetches the remaining BMC5 server pack.",
        "dependencies": {"minecraft": "1.21.1", "neoforge": "21.1.252"},
        "files": [index["files"][0], *(modrinth_entry(spec) for spec in (KUBEJS, RHINO, ACCESSORIES, OWO))],
    })
    curseforge.update({
        "name": "Goplanska — Better MC5 v53 / Roles 3.0.10",
        "version": "roles-3.0.10-installer",
        "minecraft": {
            "version": "1.21.1",
            "modLoaders": [{"id": "neoforge-21.1.252", "primary": True}],
        },
        "files": [
            curseforge["files"][0],
            {"projectID": KUBEJS["curseforge_project"], "fileID": KUBEJS["curseforge_file"], "required": True},
            {"projectID": RHINO["curseforge_project"], "fileID": RHINO["curseforge_file"], "required": True},
            {"projectID": ACCESSORIES["curseforge_project"], "fileID": ACCESSORIES["curseforge_file"], "required": True},
            {"projectID": OWO["curseforge_project"], "fileID": OWO["curseforge_file"], "required": True},
        ],
    })
    mr["modrinth.index.json"] = json_bytes(index)
    cf["manifest.json"] = json_bytes(curseforge)

    addon_new = {
        name: data for name, data in addon.items()
        if name not in {"SHA256SUMS.txt", "PLAY-3.0.8.md", "CHANGELOG-3.0.8.md"}
    }
    addon_new.update({
        f"mods/{ROLES}": roles,
        "goplanska-release.json": release_bytes,
        "download-release.json": json_bytes(download_release),
        "INSTALL.md": addon_readme(args.roles_sha256),
        "PLAY-3.0.10.md": addon_readme(args.roles_sha256),
        "CHANGELOG-3.0.10.md": changes,
        "DEPENDENCIES-3.0.10.json": json_bytes(dependency_manifest),
    })
    addon_new["SHA256SUMS.txt"] = "".join(
        f"{sha(data)}  {name}\n" for name, data in sorted(addon_new.items())
    ).encode()
    args.output_dir.mkdir(parents=True, exist_ok=True)
    outputs = {
        "prism": (args.output_dir / "goplanska-bmc5-v53-installer-3.0.10-CANDIDATE.mrpack", mr),
        "curseforge": (args.output_dir / "goplanska-bmc5-v53-curseforge-3.0.10-CANDIDATE.zip", cf),
        "addon": (args.output_dir / "goplanska-bmc5-v53-roles-3.0.10-CANDIDATE-addon.zip", addon_new),
    }
    proof: dict[str, object] = {
        "status": "local-candidate-only",
        "deployed": False,
        "minecraft": "1.21.1",
        "neoforge": "21.1.252",
        "rolesSha256": args.roles_sha256,
        "sourceLiveInstallersSha256": {"prism": BASE_MR_SHA, "curseforge": BASE_CF_SHA, "addon": BASE_ADDON_SHA},
        "upstream": {"kubejs": KUBEJS, "rhino": RHINO, "accessories": ACCESSORIES, "owo": OWO},
        "artifacts": {},
        "limitations": ["No full client launch or real join", "Not an offline full BMC5 pack", "Publish only after matching server/AutoModpack feed is verified"],
    }
    for label, (path, entries) in outputs.items():
        artifact_sha = reseal(entries, path)
        proof["artifacts"][label] = {"filename": path.name, "size": path.stat().st_size, "sha256": artifact_sha}
    (args.output_dir / "client-installer-proof.json").write_bytes(json_bytes(proof))
    print(json.dumps(proof["artifacts"], indent=2))


if __name__ == "__main__":
    main()
