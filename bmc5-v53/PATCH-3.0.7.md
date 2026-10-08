# Roles 3.0.7 — full Peeb pull and music controls

## Deployed release and source update

Candidate SHA-256:
`ff773260893687f62a1c7dfd570a5c374110e972ce84f6fd51952620a7e7f303`.

This document records the source and offline checks. [The production receipt](patch-evidence/production-3.0.7.json) confirms fresh server startup, native AutoModpack delivery and matching public downloads. The previous 3.0.6 activation is preserved in [its historical receipt](patch-evidence/production-3.0.6.json).

## Changes

- Peeb pulls all the way to its anchor while preserving momentum. `stopDistance`
  defaults to zero and can be changed in `/scooteradmin` → Peeb or
  `config/psychiatryk-peeb.properties` without another restart after code activation.
  Solid world collisions still apply.
- Geometric camera tug follows the attached endpoint; mounted grappling adds to
  the scooter's observed momentum. Peeb's body follows the scooter rotation while
  the head and tusk can aim.
- Hook attachment to an allowed enemy deals 2 damage. Actual body contact during
  the pull deals 6. Team/PvP permissions and separate attack cooldowns are retained.
- Music adds shared Pause/Resume and source volume from 0–100%. Source volume
  persists through item/source lifecycle changes and combines with local volume.
- The music menu uses grey Minecraft panels, control icons, current-song artwork,
  title, artist and Like. The grey Now Playing HUD leaves room for the minimap.
- Xaero death waypoints are hidden only in the world HUD. Map waypoints, stored
  data and player configurations remain unchanged.

Peeb configuration protocol is 2; music transport protocol is 3. Matching server
and client addons are required. This update changes no recipes, models or music
assets and adds no runtime library dependency. Xaero is compile-only for its
optional client integration.

## Checked scope

The final packed JAR passed 109 native common-class, packet-codec, config-reload
and shared-clock checks. Feature fixtures cover full pull, camera geometry,
mounted momentum, combat bounds/cooldowns, source lifecycle and permission rules,
menu layout/callbacks, HUD metadata selection and death-HUD classification.
The actual portable build reproduced the complete candidate SHA; see
[the sanitized build receipt](PORTABLE-REBUILD-3.0.7.json).

No full game client or dedicated server was launched for those fixtures. They do
not establish live rendering, network timing, audible playback or gameplay feel.

## Reproducible source build

The focused delta lives in `overlays/pull-distance-3.0.7/`. Earlier source overlays
remain unchanged. Runtime checkpoints, private catalogs, artwork and game assets
are external inputs rather than distributed files.

Supply the exact external Roles 3.0.6 checkpoint:
`7e5f26551d0e3ec0535a9bb94e8c2d6f7452a714f73071ffa147b0b51d0f8a5d`,
Java 21 and the Minecraft 1.21.1 / NeoForge 21.1.250 classpath, including ASM 9.10.1
and compile-only Xaero 26.4.2.

```sh
python build-pull-distance-3.0.7.py --base /path/roles-3.0.6.jar \
  --classpath-file /path/classpath.txt --output-dir /path/fresh-build
```

The builder verifies source and class hashes, replays the three-method PeebClient
transplant while preserving 37 unrelated methods and baseline inner classes,
then checks the complete archive hash. It performs no network or deployment action.
