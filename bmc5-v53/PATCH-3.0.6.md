# Roles 3.0.6 — music and Peeb

## Current release

Production and native AutoModpack run the same 3.0.6 artifact:
`7e5f26551d0e3ec0535a9bb94e8c2d6f7452a714f73071ffa147b0b51d0f8a5d`.
The server reached a fresh Done after a clean save; the real AutoModpack download,
TLS pin and Minecraft status were checked without launching a game client.
See [current activation evidence](patch-evidence/production-3.0.6.json).

- Previous and Skip controls operate the selected music source.
- Players share the server playback clock; late listeners seek to its position.
- Wearable jukebox sessions keep their identity, track, mode and playhead across
  death, respawn, dimension changes and re-equipping. A dropped jukebox can play
  from its loaded item entity. Unloaded or offline items are inaudible until they
  become available again; chunks are not force-loaded.
- YouTube/YT Music imports have no one-minute cooldown or arbitrary library cap.
  Ready imports appear in the library and start playback; uncached imports still
  wait for the provider's download and conversion.
- Peeb's elastic grapple preserves incoming velocity on attachment and release.
  Only newly added grapple acceleration is limited; airborne horizontal momentum
  is retained. Minecraft collision response still applies.
- Head look is transformed through the actual model hierarchy.

Focused physics, collision, lifecycle, packet, range-stream and UI fixtures passed.
The source builder reproduced the exact complete artifact. Listening across real
clients and player-visible grapple feel are not claimed as tested in this release.
The force port uses Minecraft's movement/collision units; it is not a promise of
bit-identical Unity gameplay.

## Source and rebuild

The export contains authored Peeb spring/head-look and music import/session/UI
source, plus the ProLiant metadata-only import service changes. Private catalogs,
cover art, audio, game assets and runtime JAR checkpoints are not distributed.
Prior 3.0.5 source overlays and their scope remain available as historical inputs.

Supply the exact external 53c622c5 Roles 3.0.5 checkpoint and a Java 21 Minecraft
1.21.1 / NeoForge 21.1.250 classpath (including ASM 9.10.1), then rebuild:

```sh
python build-import-fix-3.0.6.py --base /path/roles-3.0.5.jar \
  --classpath-file /path/java-classpath.txt --output-dir /path/fresh-build
```

The builder checks source fingerprints, every compiled runtime entry, exact
ordered overlay supersession and the complete resulting 3.0.6 artifact hash.
It does not launch Minecraft, use the network or control production. Existing
asset notices are retained; no broader asset license is implied by this export.
