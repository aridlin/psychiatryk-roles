# S23 next update — preview source

**Not deployed. Game tests are paused by the user. Current production and website remain v25.**

Prepared changes:
- Trim and rider hands use the evaluated steering model pose in the same frame. Rental brand text is baked into the livery.
- Workstation removes individual upgrades, trims, dye, enchantments or the name; storage refunds its contents.
- Server song loop toggle replays decoded PCM without redownloading. Headlight synchronization and registration fixes.
- Bounded, symmetric steering slows at high speed; Lime Silence is a touring/battery preset.
- Parked personal scooters accept coal by right-click: 10 coal fills an empty battery; partial stacks add 10 percent each. Only needed coal is consumed, none in Creative. Occupied, moving, airborne and rental scooters cannot use it.
- Name-tag names override the stored item name and survive pickup. `jeb_` rainbow tint is also applied in the fallback renderer.
- All 20 model skins now have matching 512x512 dimensions; build-time validation prevents the previous shared-atlas loading failure.

Checks completed without launching Minecraft:
- Compilation of the complete unified mod.
- 12,004 steering samples: bounded, symmetric, monotonic reduction with speed.
- 4,693,065 battery/fuel combinations: bounded charge and minimum coal consumption.
- All declared model texture dimensions match.

The previous v26 runtime suite passed several subsystem checks but its client log contains a model-loading failure. Therefore that build was held and the production bootstrap restored to v25. Successful startup is not a passing model-render test. Runtime validation of the revised model, name-tag pickup, fuel interaction and driving remains pending.

This preview contains code and text resources. Imported model geometry, texture assets, private music, player saves and credentials are excluded. Use the existing release asset/licensing workflow to build the full package.
