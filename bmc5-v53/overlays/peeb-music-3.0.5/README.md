# Peeb and music 3.0.5 source preparation

MC 1.21.1 / NeoForge 21.1.250 / Java 21. This is an overlay for the merged BMC
addon, not a standalone Peeb mod or a redistributed Better MC modpack.

## Recovery provenance

Temporary authored source was lost when the host reboot cleared temporary
storage. The exact `4173799b…` merged runtime JAR survived. The 57 Java families
changed from the qualified 3.0.4 baseline were reconstructed with Vineflower
1.10.1 and compile repaired. Complete historical literal source writes for
46 families were also recovered, but some are intermediate revisions. This
export labels reconstructed code truthfully; it does not claim original-source
identity or equivalent recompiled bytecode for untouched classes.

The source tree now has 58 families, including the new rope-geometry helper.
Only six Peeb families were edited and overlaid on the saved checkpoint for
`f03a1a28…`. Music, scooters, chams, bindings and bootstrap keep their checkpoint
bytes. The full recovered source is provided for future maintenance. The
`--source-check` command compiles it separately and never creates a runtime JAR.
See [the source manifest](../../patch-evidence/peeb-music-3.0.5-source-provenance.json)
for hashes and scope.

## Exact runtime rebuild

Supply the external checkpoint with SHA-256
`4173799b5497e2f7c3183b96a3550adcc7a3893968ff3311265d6c2d06e49a29`
and a named Minecraft/NeoForge Java classpath. Neither is redistributed here.
The tool does not download dependencies, inspect accounts or install the result.

```sh
python3 build.py --checkpoint /path/to/4173-checkpoint.jar --classpath-file /path/to/classpath.txt
python3 build.py --checkpoint /path/to/4173-checkpoint.jar --classpath-file /path/to/classpath.txt --source-check
python3 qualify.py --checkpoint /path/to/4173-checkpoint.jar --classpath-file /path/to/classpath.txt
```

The normal build compiles exactly `PeebGrapple`, `PeebClient`, `PeebRenderer`,
`PeebStyle`, `PeebHud` and `PeebRopeGeometry`, then copies three authored shaders.
Every other entry is preserved. It requires the exact output SHA-256
`f03a1a2856e3ce9d62de6797ce2fcf49825373252d733e6f69d9f5f60b57fa53`.
This exact rebuild uses the same Java 21 compiler and ZIP deflater environment
as the qualified build; a different compiler/deflater may produce different
archive bytes and must be qualified separately. No metadata-only rename is
used to hide such a difference.

`qualify.py` runs the actual compiled rope physics and curve helpers, verifies
client/server event bytecode timing, checks unchanged payload bytes and compiles
GLSL 150 stages if `glslangValidator` is supplied. It does not launch Minecraft.
The quick winch changes only radial speed while preserving tangential swing;
a taut-rope constraint replaces the earlier elastic spring. Camera height is
2.1 blocks by default. The focused native observer sources can be built separately; native results
must identify the exact candidate and may not be inferred from these math checks.

## Source and asset boundaries

Code retains the existing [GPL-3.0-or-later license](../../LICENSE).
The [Peeb asset notice](../../src/peeb-music-3.0.5/main/resources/licenses/peeb-assets.txt)
attributes Peeb Adventures to John Ellis / FeverdreamJohnny and the Peeb design
to Aaron. Their original assets retain their rights. The code license grants
no rights to Peeb models, clips, sprites or sound recordings.

Raw original mesh/rig data, original animation JSON, targeting and item artwork,
original effects, licensed scooter models/atlases, music tracks/cover art and
private catalogs are excluded. Their runtime entries are preserved from the
external checkpoint; they are not substitutes for an asset license. This
export includes authored shaders, language mappings, recipe/item-model JSON,
sound-event mappings, mixin registrations, metadata and attribution only.

No worlds, player inventories, account cache, credentials, JARs, binary build
outputs, raw game logs, production launch agents or private API service data are
included. Existing root/legacy/portable modules and production installer metadata
are not replaced by this source preparation.

## Import service

The [reviewed API source](../../services/ytmusic-source/README.md) is exported
separately from the active user-owned ProLiant release. It contains no server
credentials, catalogs, tracks or covers. Dependency metadata is derived, and
no missing upstream service license is inferred by the recovery.
