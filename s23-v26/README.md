# S23 v26

NeoForge 1.21.1 scooter maintenance release.

- Parked coal charging: ten coal fills an empty battery. Partial stacks charge proportionally; only needed coal is consumed. Creative does not consume coal.
- Name tags work before mounting; names persist on pickup. `jeb_` rainbow rendering works in the fallback model too.
- Bounded high-speed turning, touring Lime Silence preset, tyre scrub sound and smoothly increased drift lean.
- Trim renders in the scooter model pass. Hand grip targets use its evaluated steering pose. Rental branding is baked into the livery.
- Workstation upgrade, trim, dye, name and enchantment removal with refunds and preserved contents.
- Music loop GUI and command; decoded audio is reused for repeats.
- Headlights synchronize and register actual dynamic light sources.
- Client Flashback 0.39.7 with Connector, Forgified Fabric API and Flashback NeoForge Fixed. Recording, saving and replay opening passed in the isolated modded test profile.

Runtime checks require actual GPU loading of all 20 scooter variants. Hand alignment at five steering angles, coal interaction, name-tag pickup, workstation refunds, loop reuse and cropped/full chams framebuffer equality passed. Drift feel, trim appearance and night lighting appearance still need player feedback. Motor hum retest was waived by the user.

Release status is recorded in production-proof.json once deployment is verified. No credentials, player data or server music library included.
