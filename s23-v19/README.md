# Goplanska S23 v19

Production release deployed October 4, 2026. Minecraft 1.21.1, NeoForge 21.1.252, Java 21.

## Source layout

- `components/`: current unified roles overrides, shared chams/party renderer, admin spectate, KuKirin, Kinker Starter, Void Portals and communication-book patches.
- `original-roles-source/`: original NeoForge roles source retained by the incremental build.
- `build-inputs/`: our preserved component bytecode inputs used by the incremental unified builder.
- `dist/psychiatryk_roles-2.1.1.jar`: exact tested production artifact; checksum in SHA256SUMS.
- `verification/`: release inventories and isolated runtime check reports.

## v19 changes

Alpha-aware per-pixel occlusion for glass, grass and entities; target self-occlusion exclusion; exposed block-top fix. Reusable halftone/outline masks, item silhouettes and hover names, admin-only ore scans with ore colors, party markers, chalk and removable area markers. Phase Charm right-click and Right Alt use, live duration/cooldown config and safe-return behavior. Native two-button restart vote screen with a 50% threshold. All 25 custom recipe results indexed in JEI. Black/orange scooter with standing rider, articulated wheels and steering, 12 degrees/tick low-speed turning tapering to 5 at high speed. Backported Spears is a separate pack dependency.

## Build status

The included Python scripts document the incremental production pipeline. They currently expect the original workspace layout and its local NeoForge dependency cache; they are not a standalone Gradle build. Preserve the pinned base inputs when reproducing this release. A portable build migration remains future work. The root Gradle project is the historical Forge 1.20.1 edition.

## Assets and licensing

Project code is GPL-3.0-or-later; component notices accompany the sources. The current scooter is an adapted Sketchfab model by kovsh, linked in `components/kukirin/resources/licenses/sketchfab-scooter.txt`, with a black/orange texture and articulated derivative rig. Its license is separate from the project code. Imported raw 3D assets are not republished as standalone source files here; the deployed mod contains the embedded game derivative. Prior MakerWorld private test models are excluded. GemRender is a separate GPL dependency; its source is https://github.com/Warfactory-Official/GemRender.

## Verification

Isolated runtime checks passed with the full optional XBR pack: shared renderer, texture transparency, entity and block self-occlusion, scooter, Phase Charm, JEI recipes and restart GUI. Production startup and release inventories were verified. Forced JVM-crash recovery and the actual server resource-pack acceptance prompt were not visually tested. Electric motor sounds are a proposal for the next update, not part of v19.
