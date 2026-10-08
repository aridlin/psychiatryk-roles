# Roles 3.0.9 — queued, not activated

As of 8 October 2026, the working production and website addon remains loader-fixed 3.0.8 (`56cc13966c2ed202ff54ac9e0b401fce32325842d93fff2be22b1ee14de9b614`). The fresh 3.0.9 candidate (`a513a1e338f0651648dfa32ecf9f3049daab312c2304183083337ecbc43572de`) is queued for the **normal 9 October 04:00 Europe/Warsaw restart**. A not-before guard prevents earlier activation. No manual restart or host pause occurred.

## Changes

- Server-authoritative scooter admin settings broadcast to everyone, including join synchronization; minimum high-speed turning remains usable.
- Peeb pulls same-party teammates toward himself, with authoritative target movement.
- Audible custom music pauses and then resumes vanilla background music.
- Narrow EMF Video Settings compatibility repair and supported ModLauncher loader gates.
- Peeb turns the whole animated body toward movement; eyes stay with the original head rig and dyes cover the tusk.
- Admin-enabled grapple stepping accepts collision-safe progress around terrain corners while rejecting blocked walls and ceilings.

The client and server must update together for the new handling and Peeb protocol versions. AutoModpack will deliver the activated release. The optional Peeb stepping toggle is already enabled on this server, while the new corner and rendering code remains pending until the scheduled patch.

## Evidence

[Native qualification](patch-evidence/native-qualification-3.0.9.json) records 152,021 fresh headless checks against the final packed candidate and a byte-identical rebuild. This covers native collision, render geometry/draw calls, codecs, permissions, lifecycle and real Sponge loader transformations. It does **not** establish visible gameplay, sound or a live multiplayer test.

[Queue receipt](patch-evidence/queued-3.0.9.json) confirms the pending flag was published last and current production payloads, generated feed, crop config, Peeb settings and historical backup trees stayed unchanged. [Post-queue health](patch-evidence/health-after-queue-3.0.9.json) verifies the existing Minecraft service and actual pinned AutoModpack delivery still serve working 3.0.8.

[Authored source and portable builder](overlays/next-patch-3.0.9/README.md) exclude original-game inspection files, compiled payloads, model/audio assets and private deployment inputs. The builder requires the exact loader-fixed baseline plus a NeoForge development classpath. Asset licenses remain separate from the authored code license.
