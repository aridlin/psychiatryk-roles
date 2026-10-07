# Wearable jukebox — Better MC5 addon 3.0.3

The vanilla jukebox can be equipped in the existing Accessories **back** slot. It occupies the same slot as a backpack. Its normal block model is visible on the wearer's back and follows the torso, including crouching. The equipment visibility toggle works normally.

With it equipped, open the inventory and click **Jukebox music**, or use `/jukebox`. The searchable browser contains the same server songs and bundled tracks as the scooter. It supports Play, Stop and Loop. `/jukebox stop` and `/jukebox loop true|false` also work.

Nearby players in the same dimension hear positional music up to the existing 64-block limit. Music stops when the jukebox is unequipped or replaced, the wearer dies, disconnects or changes dimension. Re-equipping does not resume playback. Loop preference follows the item; normal and admin testing inventory banks remain separate.

## Audio implementation

Music reuses the existing asynchronous authenticated HTTP cache and mono WAV decoder. The maximum of four active or pending sources remains unchanged. Source position follows the wearer's body rather than their head.

When Sound Physics Reverb is installed and enabled, only these moving music sources receive an environment update every ten ticks on Minecraft's sound executor. Updates call Sound Physics' actual OpenAL processing API and retain the existing environment smoothing. The global moving-sound setting is unchanged. Without Sound Physics, playback remains positional.

Packets select only the requesting player's currently equipped jukebox. Server item tokens plus live item identity invalidate stale menus and copied equipment. Delayed decoding cannot revive a stopped session; bounded pending metadata handles players whose tracking and equipment sync arrive later.

## Build and qualification

`python3 work/bmc5-migration/wearable-jukebox/build.py` overlays only the scoped equipment/music implementation on the fingerprint-pinned 3.0.2 main addon. It preserves the original detailed scooter model, its renderer, steering, storage, pickup, recipes and licenses. Do not regenerate the main addon from the portable primitive scooter project.

`build/build-proof.json` confirms compilation and archive scope only. Runtime results belong in the separate `qa` fixture and qualification receipt. An unqualified candidate must not be published or installed on production.

This overlay does not copy the private music catalog or its authentication into public archives, Prism or AutoModpack.

## Veinminer configuration fix in this release

The native VeinMining server configuration uses `blocks = "ORES"` instead of
an empty deny list. Connected ores remain vein-mineable without an enchantment
(`maxBlocksBase = 50`); ordinary terrain and construction blocks are excluded.
FallingTree retains its separate tree-cutting behavior. The paired server and
AutoModpack configuration is shipped alongside this unchanged wearable JAR.
