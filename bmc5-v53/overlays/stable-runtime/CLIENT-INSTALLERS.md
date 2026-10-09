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
`psychiatryk_roles-3.0.0-bmc5.jar`. Prism resolves KubeJS build 377,
Rhino build 85, Accessories beta.53 and oωo beta.1 through pinned official
Modrinth file URLs and SHA-512 hashes; CurseForge resolves the corresponding
project/file IDs. Accessories and oωo must load before NeoForge can accept
the embedded Roles mod on a fresh import. AutoModpack 4.0.6
downloads the remaining server pack on first connection. Neither public
installer is a complete offline BMC5 pack, and neither forces a resource
pack. The retained `servers.dat` contains only the server display name and
`goplanska.pl`.

The upstream records used for the new script dependencies are
[KubeJS build 377 on Modrinth](https://modrinth.com/mod/kubejs/version/THIGFPwf),
[Rhino build 85 on Modrinth](https://modrinth.com/mod/rhino/version/cQ4POTah),
[KubeJS build 377 on CurseForge](https://www.curseforge.com/minecraft/mc-mods/kubejs/files/8843626),
and CurseForge's file record for Rhino project 416294, file 8218748. The
first-launch dependency records are
[Accessories beta.53 on Modrinth](https://modrinth.com/mod/accessories/version/1.1.0-beta.53%2B1.21.1),
[oωo beta.1 on Modrinth](https://modrinth.com/mod/owo-lib/version/NMCHU6DZ),
[Accessories beta.53 on CurseForge](https://www.curseforge.com/minecraft/mc-mods/accessories/files/7583320),
and [oωo beta.1 on CurseForge](https://www.curseforge.com/minecraft/mc-mods/owo-lib/files/6785734). The
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

The original 3.0.10 fallback JAR has SHA-256
`3d4842404a38b205a3ca0541ae6ba5a4b4423ef0ecc46fcc094baec5f7970964`.
The current canonical source build at `build/psychiatryk_roles-3.0.10-bmc5.jar`
adds generic runtime-item artwork and has SHA-256
`eb4b1685205ba1ac3432b1708572d829e9004193ca8857b8b05798bc48f6ce12`.
Both exact JAR hashes and the separate 298-JAR smoke results are recorded in
`QUALIFICATION.md`. The prior local archives below are kept as rollback
artifacts and still contain the **original 3d48 JAR**:

| Local artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| `build/release-3.0.10/psychiatryk-3.0.10-payload.zip` | 26,355,048 | `6d62063d4e7a27ba8fa3d9c77f4a10869e839704ecae3e54d817389d0c1fa609` |
| `build/client-installer-3.0.10/ready/goplanska-bmc5-v53-installer-3.0.10.mrpack` | 10,227,365 | `3025198f21f7d47b17242b71e3ae4093d5d5f7e0e923407b3172be0305eb940f` |
| `build/client-installer-3.0.10/ready/goplanska-bmc5-v53-curseforge-3.0.10.zip` | 10,226,775 | `dccbb45c1ab7f8aabf1be172b60cfb9266226aab29353889df1bcc3383fb94d4` |
| `build/client-installer-3.0.10/ready/goplanska-bmc5-v53-roles-3.0.10-addon.zip` | 10,229,012 | `14e48ee137e62b2f150bdf5b78f422bd20c416cffc4af17db719868d3566ddbb` |
| `build/client-installer-3.0.10/private/psychiatryk-bmc5-v53-3.0.10-client-mods-PRIVATE.zip` | 683,108,804 | `86d2bbefb7fe646882fdd0afbacb4c69cad56cd59b4b077281e0a7452e50c72b` |

The current `eb4b...` source scripts independently reproduced the following
local-only item-candidate archives, each with the same embedded Roles JAR:

| Item-candidate artifact | SHA-256 |
| --- | --- |
| `build/release-3.0.10-item-candidate/psychiatryk-3.0.10-payload.zip` | `55069e77197459e97abafb67d0a8dd0da7215065808656fec95c7294f200346f` |
| `build/client-installer-3.0.10/item-candidate/goplanska-bmc5-v53-installer-3.0.10-CANDIDATE.mrpack` | `963af4281066f77d1246a2cfa29f4f78494f14cf31fd9a60395870291ae915c3` |
| `build/client-installer-3.0.10/item-candidate/goplanska-bmc5-v53-curseforge-3.0.10-CANDIDATE.zip` | `583bcde5b360bfdec4db7bf0e54d922561b6193fdd0f58315fe754fb93a27172` |
| `build/client-installer-3.0.10/item-candidate/goplanska-bmc5-v53-roles-3.0.10-CANDIDATE-addon.zip` | `5ec8135eb57741ec06d254fecf07cd46bc0df4ca4564d1027681addce9f000d0` |

The original public artifacts in `ready/` remain **local and unpublished**. The
private ZIP must not be uploaded. The server bundle has eight paired payload
files plus a checksummed manifest; its server and AutoModpack copies match.
The private ZIP has 356 hashed JARs and exactly one Roles mod ID. The three
public archives have valid ZIP readback, one Roles JAR each, and no unsafe
paths or stale 3.0.8/NeoForge 21.1.250 text. A wrong Roles SHA is rejected.

After the initial site publication, a fresh-import audit found that the
`item-candidate/` bootstraps lacked Accessories and its required oωo library.
AutoModpack has no selected modpack on the first launch, so it cannot fetch
those before NeoForge validates required dependencies. The corrected
installers are in `build/client-installer-3.0.10/ready-bootstrap-deps/`:

| Corrected bootstrap | Bytes | SHA-256 |
| --- | ---: | --- |
| `goplanska-bmc5-v53-installer-3.0.10.mrpack` | 10,234,029 | `cfd869ee41c339ec9b8d172a54df92bd4a47b69e6437c725c2d3002fbbcb54d8` |
| `goplanska-bmc5-v53-curseforge-3.0.10.zip` | 10,233,152 | `b4cd1504765bc925385808164e5b77c22967f7c47c48f50d7aa894c911a74d42` |

The five Modrinth download URLs (AutoModpack, KubeJS, Rhino, Accessories,
oωo) were downloaded and their SHA-512 and lengths matched the index. The
fresh-start dependency regression passes for both launcher manifests. The
manual addon for an existing full BMC5 installation is unchanged. Real
client import, join and rendering are still separate checks.

Build the current item candidate into separate directories, leaving the
original `ready/` and private fallback artifacts untouched:

```sh
python bmc5-v53/overlays/stable-runtime/package_release.py \
  --output-dir bmc5-v53/overlays/stable-runtime/build/release-3.0.10-item-candidate

python bmc5-v53/overlays/stable-runtime/build_client_installers.py \
  --roles-jar bmc5-v53/overlays/stable-runtime/build/psychiatryk_roles-3.0.10-bmc5.jar \
  --roles-sha256 eb4b1685205ba1ac3432b1708572d829e9004193ca8857b8b05798bc48f6ce12 \
  --output-dir bmc5-v53/overlays/stable-runtime/build/client-installer-3.0.10/item-candidate

python bmc5-v53/overlays/stable-runtime/build_private_client_mods.py \
  --roles-jar bmc5-v53/overlays/stable-runtime/build/psychiatryk_roles-3.0.10-bmc5.jar \
  --roles-sha256 eb4b1685205ba1ac3432b1708572d829e9004193ca8857b8b05798bc48f6ce12 \
  --output-dir bmc5-v53/overlays/stable-runtime/build/client-installer-3.0.10/item-private
```

These scripts never contact the live server, website, or player instance.
Local ZIP integrity and dependency metadata do not prove Prism/CurseForge
import, game launch, authentic player join, or client rendering. Verify
those separately on a host with enough free RAM before publication. Only
after the matching server and AutoModpack feed are active should the local
candidate artifacts be renamed to the public download filenames.
