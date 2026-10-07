# Portable Chams

Client-only outline and occluded halftone renderer for Minecraft 1.21.1 / NeoForge.
It contains no role system, parties, scooter implementation, ore scan, networking,
or dependency on another mod. Minecraft supplies Gson, JOML and LWJGL.

## Install and build

Install `portable-chams-1.0.0.jar` in the client's `mods` directory. Java 21,
Minecraft 1.21.1 and NeoForge 21.1 are the only required runtime components.
The isolated runtime proof uses NeoForge 21.1.252; the development Gradle baseline
is 21.1.219. Older 21.1 releases were not individually tested.

Use a normal Gradle installation with Java 21: `gradle build`.
No Gradle wrapper binary is included. The private cached offline helper is not part of this source snapshot; normal Gradle builds resolve official dependencies.

## Explicit target API

```java
import pl.aridlin.portablechams.api.Highlights;
import pl.aridlin.portablechams.api.HighlightStyle;

AutoCloseable registration = Highlights.register(
    ResourceLocation.fromNamespaceAndPath("my_mod", "targets"), sink -> {
        sink.entity(entity, HighlightStyle.halftone(0xFFFF55));
        sink.block(blockPos, HighlightStyle.outline(0xFFAA55));
        sink.plane(areaId, areaBounds, HighlightStyle.halftone(0x55FFFF));
    });
// Close the registration when the adapter is unloaded.
```

Callbacks run each rendered frame. Supply current targets and keep permissions,
selection, visibility rules and target lifetime in your adapter. The collector
must not escape the callback or be used on another thread. Entities currently
supported are living entities and dropped items in the current client world.
Boxes, horizontal planes and camera-facing glyphs use caller-managed stable UUIDs.
Only loaded client data is examined; the module does not request hidden server
data. A client renderer is not a server permission boundary.

An optional adapter can depend on this API without making either feature a
required dependency of the other. No automatic adapter for Psychiatryk Roles,
StorageFinder, WAY, or scooters is included.

## Manual controls and hot config

Client commands: `/portablechams mark`, `block`, `clear`, and `toggle`.
There are no automatic targets on a fresh install. Manual selections clear when
the client world changes. First-person ridden vehicles are suppressed.

`config/portable-chams.json` is reloaded without restarting:

```json
{
  "enabled": true,
  "maxDistance": 64,
  "maxTargets": 16,
  "dotSpacing": 14,
  "outlineRadius": 2,
  "opacity": 1.0
}
```

Bounds: distance 4–128 blocks, targets 1–32, spacing 4–64 pixels, border radius
1–6 pixels, opacity 0–1. Invalid content keeps the previous complete settings.
This renderer prioritizes a bounded number of nearby targets; it does not clone
players beyond the client's entity tracking range or provide distant dots.

## Rendering and limits

The extracted authored implementation uses actual textured silhouettes for
living entities/items, loaded block selection shapes, texture-alpha-aware cover
for grass/glass holes, and entity occlusion with target-self exclusion. Solid
geometry thickness controls the halftone density. The screen pixel pattern is
anchored to the target projection. Depth/shape sampling is an approximation of
loaded world geometry, not an exact reconstruction of arbitrary shader effects.

Immersive Portals, when present, is detected reflectively to skip nested portal
rendering. It is optional and not part of the bare-runtime test; compatibility
with every portal/shader combination is not claimed. Unusual third-party
renderers may need a dedicated adapter. Rendering cost still grows with targets
and nearby cover; the defaults intentionally cap it.

## Verification

The private QA fixture performed 17 API/config/resource checks. The isolated Java 21 software-rendered
client additionally passed 15 runtime checks on Minecraft + NeoForge + this mod:
five compiled shaders; actual entity/item/block/plane/glyph output; through-wall
halftone pixels; no GL errors; file reload; target caps; provider cleanup.
See `qa/runtime-report.json`. The fixture used no Roles, scooter, party or custom
render-library mod and did not launch or alter the main game or production.

Private launcher/account/world files, copied configurations and logs are excluded
from source distribution. Authored QA helper source can be reused separately.

## License

MIT for this project's authored Java/shaders. See `LICENSE` and
`THIRD-PARTY-NOTICES.md`. Minecraft and NeoForge are external prerequisites and
are not redistributed in this project.

## BMC renderer compatibility update

The current source owns immediate per-RenderType mask buffers. Iris replaces
the world's BufferSource with a deferred implementation whose per-type flush
can be a no-op; sharing it leaked mask geometry into ordinary world rendering.
Mask batches now flush while the mask framebuffer is bound and are discarded
on failure. The outer render pass also restores Minecraft and raw GL state.
This update adds no dependency on Iris. The merged full BMC/NeoForge21.1.250
client passed 25 real checks, including through-wall output, provider off/on/off,
unchanged flower/drop/sky colors and a Sophisticated Backpacks GUI. Shaders were
disabled for this fixture; arbitrary shader-pack compatibility is not claimed.
