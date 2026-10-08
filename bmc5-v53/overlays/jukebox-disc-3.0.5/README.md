# Jukebox spinning-disc follow-up

Target: Minecraft 1.21.1 / NeoForge 21.1.250 / Better MC5 v53, Psychiatryk Roles 3.0.5. This is a narrow source overlay for the approved `f03a1a2856e3ce9d62de6797ce2fcf49825373252d733e6f69d9f5f60b57fa53` Peeb checkpoint. It keeps that checkpoint's Peeb implementation and assets byte for byte.

## Behavior

A placed jukebox with an active custom-song source displays a physical vinyl record. Missing or pending cover metadata shows dark grooves, a green vanilla record label, and asymmetric cream/orange paint so its rotation remains visible. Available cover art is masked into the disc face. Rotation uses the source's elapsed seconds at 33⅓ RPM. The raised face and thin edge separate the disc from the jukebox top without drawing through walls.

A worn jukebox without cover art retains a vanilla jukebox side panel. When a cover arrives, the back panel uses it normally. The renderer follows active source sessions, so stopping or removing a jukebox removes the visual. It does not create sounds, alter playback rules, change controls, or modify Peeb.

## Build

Supply your exact checkpoint JAR and a NeoForge development classpath. Neither the private checkpoint nor local library paths are shipped in this source export. Java 21 is required.

```sh
python build.py --base /path/to/f03a-checkpoint.jar \
  --classpath-file /path/to/neoforge-development-classpath.txt \
  --output /path/to/output --verify-geometry
```

The build verifies base and source hashes, compiles only the two renderer families, and rejects unrelated class outputs. With the release compiler/dependencies it reproduces frozen candidate `e0f6a9df96d249ff7a6d9477ddcc04bc90e5b446c3210f3cf47e8d887be7e66d`; `matches_frozen_candidate` reports the exact archive comparison. `build-proof.json` records the resulting digest, changed entries, and exact Peeb preservation. It never starts Minecraft, updates a profile, or deploys a server.

## Verification and scope

The actual submitted vertex geometry passes 24,023 checks for finite positions, top-face winding, complete quads, texture bounds, rotation, invalid timestamps, and single camera subtraction. Compilation and geometry checks do **not** establish native visual success. The parent release's qualification notes carry the separate native screenshot/render result.

The exact frozen candidate separately passed [26 native playback/source checks and 12 actual framebuffer checks](../../patch-evidence/jukebox-disc-3.0.5-native.json) in the full Better MC client. No-cover and cover-art pixels rotated by about 50 degrees between frames; stop and block removal cleared the visual. This used software OpenGL with shaders disabled. It does not qualify a new shaderpack or activate a production release.

The historical source/provenance under `bmc5-v53/src/peeb-music-3.0.5` is intentionally unchanged. `source-provenance.json` identifies only these new follow-up sources. The fallback uses Minecraft's installed textures and the checkpoint's existing one-pixel white texture; no new image or model assets are included here, and no third-party asset license is granted by this code export.
