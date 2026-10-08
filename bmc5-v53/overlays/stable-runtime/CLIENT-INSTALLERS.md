# Psychiatryk 3.0.10 client distribution staging

The live Prism `.mrpack` and CurseForge ZIP are nine-entry AutoModpack
installers, not offline copies of Better MC5. Both currently pin NeoForge
21.1.250 and Roles 3.0.8 in their documentation. Their exact read-only HTTPS
copies are under ignored `build/client-installer-3.0.10/base-live-*`; the
packaging script verifies all three base archive hashes before using them.
The server runs NeoForge 21.1.252, so a fresh 3.0.10 installer must pin
21.1.252 on both launcher formats.

`build_client_installers.py` produces a local Prism `.mrpack`, CurseForge
ZIP, and manual first-party addon ZIP. It embeds the exact Psychiatryk
3.0.10 JAR under AutoModpack's stable managed filename
`psychiatryk_roles-3.0.0-bmc5.jar`. Prism resolves KubeJS build 377 and
Rhino build 85 through pinned official Modrinth file URLs and SHA-512 hashes;
CurseForge resolves the corresponding project/file IDs. AutoModpack 4.0.6
downloads the remaining server pack on first connection. Neither public
installer is a complete offline BMC5 pack, and neither forces a resource
pack. The retained `servers.dat` contains only the server display name and
`goplanska.pl`.

The upstream records used for the new script dependencies are
[KubeJS build 377 on Modrinth](https://modrinth.com/mod/kubejs/version/THIGFPwf),
[Rhino build 85 on Modrinth](https://modrinth.com/mod/rhino/version/cQ4POTah),
[KubeJS build 377 on CurseForge](https://www.curseforge.com/minecraft/mc-mods/kubejs/files/8843626),
and CurseForge's file record for Rhino project 416294, file 8218748. The
local JAR hashes are checked against the official Modrinth file records.
The first-party JAR contains its GPL code notice, model license, and
attribution files; publish matching source before uploading the binary.

`build_private_client_mods.py` separately creates a **private**
356-JAR inventory ZIP from the disposable QA profile. It replaces the
profile's older Roles JAR with the final candidate bytes and renames that
one file to the stable managed name. The build refuses any second JAR
declaring `psychiatryk_roles`. It writes per-JAR SHA-256 values and checks
the archive by readback. Do not upload this ZIP: many third-party JARs have
restrictive distribution terms. It is mods-only; the staged QA profile is
missing 33 exact non-mod feed files and differs in seven configs.

The frozen Roles JAR is `build/psychiatryk_roles-3.0.10-bmc5.jar`, SHA-256
`3d4842404a38b205a3ca0541ae6ba5a4b4423ef0ecc46fcc094baec5f7970964`.
After the exact 298-JAR server smoke completed a clean stop, the local builds
were reproduced and checked:

| Local artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| `build/release-3.0.10/psychiatryk-3.0.10-payload.zip` | 26,355,048 | `6d62063d4e7a27ba8fa3d9c77f4a10869e839704ecae3e54d817389d0c1fa609` |
| `build/client-installer-3.0.10/ready/goplanska-bmc5-v53-installer-3.0.10.mrpack` | 10,227,365 | `3025198f21f7d47b17242b71e3ae4093d5d5f7e0e923407b3172be0305eb940f` |
| `build/client-installer-3.0.10/ready/goplanska-bmc5-v53-curseforge-3.0.10.zip` | 10,226,775 | `dccbb45c1ab7f8aabf1be172b60cfb9266226aab29353889df1bcc3383fb94d4` |
| `build/client-installer-3.0.10/ready/goplanska-bmc5-v53-roles-3.0.10-addon.zip` | 10,229,012 | `14e48ee137e62b2f150bdf5b78f422bd20c416cffc4af17db719868d3566ddbb` |
| `build/client-installer-3.0.10/private/psychiatryk-bmc5-v53-3.0.10-client-mods-PRIVATE.zip` | 683,108,804 | `86d2bbefb7fe646882fdd0afbacb4c69cad56cd59b4b077281e0a7452e50c72b` |

The public artifacts in `ready/` remain **local and unpublished**. The
private ZIP must not be uploaded. The server bundle has eight paired payload
files plus a checksummed manifest; its server and AutoModpack copies match.
The private ZIP has 356 hashed JARs and exactly one Roles mod ID. The three
public archives have valid ZIP readback, one Roles JAR each, and no unsafe
paths or stale 3.0.8/NeoForge 21.1.250 text. A wrong Roles SHA is rejected.

Rebuild from the frozen candidate if any input changes:

```sh
python bmc5-v53/overlays/stable-runtime/build_client_installers.py \
  --roles-jar bmc5-v53/overlays/stable-runtime/build/psychiatryk_roles-3.0.10-bmc5.jar \
  --roles-sha256 3d4842404a38b205a3ca0541ae6ba5a4b4423ef0ecc46fcc094baec5f7970964

python bmc5-v53/overlays/stable-runtime/build_private_client_mods.py \
  --roles-jar bmc5-v53/overlays/stable-runtime/build/psychiatryk_roles-3.0.10-bmc5.jar \
  --roles-sha256 3d4842404a38b205a3ca0541ae6ba5a4b4423ef0ecc46fcc094baec5f7970964
```

These scripts never contact the live server, website, or player instance.
Local ZIP integrity and dependency metadata do not prove Prism/CurseForge
import, game launch, authentic player join, or client rendering. Verify
those separately on a host with enough free RAM before publication. Only
after the matching server and AutoModpack feed are active should the local
candidate artifacts be renamed to the public download filenames.
