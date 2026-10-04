# Scooter sound upgrades

At a smithing table (no template required), combine the scooter with:

- **Jukebox:** insert a music disc by right-clicking the scooter with it. The disc stays with the scooter when picked up. `/scootermusic disc` starts it again; inserting a different disc returns the previous one.
- **Note block:** play a server WAV file with `/scootermusic filename.wav`. Server files go in `config/goplanska-scooter/audio/`. Filenames only, no URLs or paths. `/scootermusic stop` stops either source.
- **Chest:** E while riding (uses your inventory keybind), or `/scooterstorage` opens 27 slots while riding or aiming at the scooter. Contents stay on pickup.

Upgrades combine and retain enchantments, ownership, armor trim and contents. A bound scooter only permits its owner to operate these features.

WAV audio is decoded off the render thread, converted to mono for OpenAL positional audio, and emitted at the moving scooter's speaker height. It uses Minecraft's sound engine, allowing Sound Physics Remastered to apply wall absorption, muffling and reflections. Client `sound_physics_remastered/soundphysics.properties` must have `enabled=true` and `update_moving_sounds=true`. `/scootersettings` has a music volume slider; the Blocks volume also controls these speakers.

Accepted WAV: RIFF WAV, mono/stereo, 8–96 kHz, formats supported by Java Sound. Encoded and decoded audio each limited to 16 MiB. Four simultaneous server WAV sessions maximum, transfer paced to 64 KiB/player/tick, listeners within 64 blocks. Ten-minute maximum session lifetime. Entering range starts the clip from the beginning. WAV playback stops on pickup; use the command again after placing it. No global/non-positional playback fallback.

This is a candidate, not a production deployment. Runtime acoustic verification is recorded separately.

## Scooter paint and trim
Use a smithing table with an empty template slot, the scooter in the base slot and a dye in the addition slot. All 16 dyes recolor only the orange accents. Repainting retains binding, enchantments, armor trim, music and chest contents. Armor trims remain compatible and use their own material colors. The result preview uses the same colored 3D model as the world renderer, shown from above and in front. Inventory icons follow the selected dye.

First-person riding hides the ridden scooter's chams and marker. Other chams use the entity's real renderer and render offset, including riding, crouching and animation transforms.

## Bundled original music
With the note-block upgrade, use `/scootermusic chiki_ride.wav` or `/scootermusic night_motor.wav`. These original hardbass instrumentals ship with the mod and require no server-file upload. `/scootermusic list` lists them; `/scootermusic stop` stops playback. They use the same moving, wall-muffled speaker system.

## Riding and enchantments
- Space: use a wind charge from hotbar slots 1–9. Survival consumes one; creative keeps it. Without a hotbar charge, grounded Space hops about 1.5 blocks.
- While airborne and touching a wall, release and press Space again to kick away, redirect the scooter and gain a small velocity boost. Wall kicks need no wind charge and have a short anti-repeat delay.
- Descent gravity is reduced by 15% while occupied; ascent stays unchanged. Scooter steps obstacles up to 1.3 blocks even in the air.
- Double-tap forward within 350 ms: Lunge, only with the existing Lunge enchantment; two-second cooldown.
- Efficiency: faster top speed. Quick Charge: stronger acceleration. Sweeping Edge: faster steering. Density: stronger braking and grip. Knockback: impact shove. Piercing: impact damage. Frost Walker: predictive ice across source water. Depth Strider: water movement. Soul Speed: soul-soil speed. Feather Falling: rider landing protection. Wind Burst: taller charge jumps. Loyalty: owner recall when bound.
- Sharpness, Mending, Unbreaking and other enchants with no scooter effect are rejected.
- Creative Tools & Utilities includes Lime Silence (lime accents, emerald Silence trim, bound, chest and note block), Orange Roadrunner and Blue Ice Runner presets. Binding assigns the player on placement. Silence is a trim pattern; it does not mute the motor.

Motor hum retains a speed-related baseline; signed acceleration strongly raises load/pitch/volume, and deceleration lowers them. Both signals are smoothed. Wind noise remains speed-driven.
