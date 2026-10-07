# Portable Scooters (NeoForge 1.21.1)

Standalone extraction of the tested v34 scooter controls. Requires only Minecraft 1.21.1, NeoForge 21.1.248 or later in the 21.1 series, and Java 21. Install on both server and clients. No Roles, Create, Flywheel, GemRender, party, poker, or dynamic-lighting mod is required.

Build with Java 21: `./gradlew build`. `build-local.py` is a private-workspace offline development helper; the normal distributable build is Gradle.

The mod keeps the `goplanska_kukirin` registry IDs to preserve item/entity data. **Do not install beside the old merged Psychiatryk Roles JAR containing that same mod ID.** Extracted features must replace the merged scooter component, not duplicate it.

## Included

Server-owned scooter entity, v34 signed controls and raked steering axis, acceleration/boost/brake/drift, battery/coal charging, enchantment effects, bound recall, storage/settings/help/music menus, smithing upgrades/dyes/trims, rental variants, recipes and a scooter-only creative tab. WAV playback uses bounded asynchronous HTTPS downloads with SHA-256 checks and a local cache. Two original CC0 instrumental tracks are bundled in the tested binary.

An original 1000-triangle primitive model is rendered using Minecraft's ordinary entity buffer, with the same steering axis/grip/wheel locations. Generate it with `python tools/generate_model.py`. No imported model, UV atlas, Sketchfab texture, or third-party logo is included. Rental variants use colors; the production-specific livery is not distributed. The isolated bare client verified native fallback spawn/mount and neutral/A/D poses without GL errors; this is not a production performance benchmark.

## Conservative defaults

`config/goplanska-scooter/portable.properties` hot-reloads: block breaking, collision damage, inverted gravity and rental Nether explosions are disabled initially. Rental world spawning is off; no-parking biome list is empty. Other handling/battery/client settings retain the existing configurable scooter files.

Rental payments default to inventory iron nuggets. Change `currency` to an item ID. Server integrations can register `RentalPayments.Provider`; they should maintain real backing rather than mint unbacked redeemable credits. No Roles class or poker account is linked.

## Optional integrations and limits

* Create charging: guarded reflection detects a powered kinetic block beside a Create depot. No Create class is linked. Coal charging works without Create.
* Shoulder Surfing mouse steering: guarded camera reflection. Its plugin source is preserved separately in `optional-adapters/shouldersurfing`.
* JEI extra descriptions and Spears Lunge compatibility: preserved separate optional adapter sources. Vanilla/custom recipe serializers remain in core.
* Real terrain headlights require the separate dynamic-lighting adapter and LambDynamicLights. Core has a client registration hook `ScooterHeadlights.register(Runnable)`; it does not create fake world-light blocks. Adapter source is preserved in `optional-adapters/lights` and is not included in the base JAR.
* Sound Physics smoothing is an optional pseudo-mixin; absent mod is accepted.
* Chams is an independent client API module. No highlight provider is automatically registered by this core yet.

The optional adapter source folders are not compiled artifacts or required dependencies. They need their own compatible loader metadata/build and pack-level QA before distribution.

## Verification

The standard Gradle build compiles with only NeoForge's development dependencies. Isolated dedicated-server startup lists exactly Minecraft, NeoForge and Portable Scooters, loads 1315 recipes, reaches `Done`, and shuts down saving every dimension. The fixture is private `run/server`; never distribute that world/log/config tree. The sanitized client proof in qa-results/client-runtime-report.json separately covers actual spawn/mount and native fallback poses.

Code and authored primitive model: GPL-3.0. Original bundled instrumentals: CC0 (notice next to music). Existing production model is separately licensed and deliberately omitted.

Source publication excludes all audio files. The two original CC0 tracks remain in the tested distributable; a build from this source snapshot omits optional bundled tracks. Gradle builds need Java 21 and dependency downloads.

## URL music source update

The current source includes the tested URL music/jukebox component. Configure
`config/goplanska-scooter/music-sources.json` on the server only; its schema and
limits are documented in [the music guide](../../bmc5-v53/music/README.md).
Shift-right-click a jukebox for the shared search browser. The scooter Music
button requires its note-block upgrade. Both have Loop and Stop controls.
Both server and clients must update together because the music protocol changed.
The merged full-BMC candidate passed 24 client and 19 server music checks; the
new source was not separately rerun as a bare standalone binary. No private
catalog, credentials or user-owned audio is part of this source publication.
