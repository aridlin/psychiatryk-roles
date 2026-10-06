# Point Blank scope / Immersive Portals stencil compatibility

Original, narrow client compatibility code for Point Blank 2.2.0 on Minecraft
1.21.1 / NeoForge with the installed Immersive Portals and Sable stack.
No upstream mod implementation source is included.

## Reproduced issue

In a disposable first-person world, adding a magnifying scope to an M4A1 and
equipping it made the terrain disappear. Aiming restored the terrain; unscoping
hid it again. Removing the gun restored rendering in that fixture.

Point Blank's unscoped stencil-mask cleanup left the stencil write mask at zero.
Immersive Portals clears the previous frame's stencil before setting a new write
mask. Aiming ran an additional scope cleanup that restored mask 255.
The first stencil-only comparison corrected write-mask/cache state but still
reproduced disappearing terrain. The remaining function was `EQUAL 1/2` from the
scope. An additional 122-sample control showed world rendering begin with stencil
enabled, comparison `EQUAL 1`, and every one of 409,920 stencil pixels zero. Plain
M4A1, empty, unselected and removed-gun controls used `ALWAYS` and rendered terrain.
Immersive Portals enables its next outer-world stencil test before selecting a
new function, so that scope comparison leaked into world rendering.
The paired cleanup below restores the outer-world baseline; the final candidate's
runtime result is recorded separately.

The mods also mix raw GL stencil operations with Minecraft's cached RenderSystem
calls. Raw portal changes can leave that cache stale, causing scope mask operations
to be skipped even though the driver state differs.

## Changes

- Point Blank's scope-mask cleanup restores write mask 255. The subsequent lens
  passes retain their existing `KEEP` operations and therefore do not change the
  stencil values.
- Mask, lens and scoped-glow cleanup restore function `ALWAYS`, reference zero and
  operations `KEEP`, while stencil remains disabled as in the original cleanup.
  Subsequent scope and nested-portal passes explicitly select their own comparisons.
- At specific Point Blank mask, lens and scoped-glow call sites, the original
  RenderSystem call updates its cache and a matching raw GL call applies the state
  even if the cache was stale.

There are no GL state queries in these hooks, no framebuffer replacement, no scope
disabling and no global render-state resets. The candidate contains exactly two
original mixin classes and one client-only mixin configuration.

## Build and verification

Use Java 21 and a NeoForge development classpath including installed Point Blank
2.2.0, Minecraft, LWJGL and Mixin:

```sh
python build.py --classpath-file /path/to/classpath.txt
```

The overlay must be integrated into the existing unified mod's mixin metadata. It
is not a standalone mod. `build-proof.json` records exact bytes. Runtime results
are supplied separately in the release Point Blank QA report, which records the
tested candidate hash. The disposable software-renderer test does not replace a
check on the user's hardware and production world.

The final `ac32a763` candidate passed the synthetic visual comparison: terrain
remained visible equipped, after inventory and unscoped; the scope retained its
magnifying view. An actual 3×3 Immersive Portals aperture also remained visible
through those phases, and its recursive world retained its `EQUAL 1` stencil test.
All 191 observed framebuffer/state probes were complete with GL error zero.
See the QA report for full hashes, phase measurements and source-profile checks.

Allocation, framebuffer-wrapper and write-mask-only hypotheses were tested
separately and did not fix the reproduced world loss. None should be published as
a verified scope fix without the successful final render-boundary comparison.
