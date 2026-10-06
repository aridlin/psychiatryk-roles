# Scooter camera and spear compatibility

Authored integration sources are in `work/kukirin/src/pl/aridlin/kukirin`; this directory contains the reproducible component compiler and hashes. No upstream decompiled source or private player data is included.

## Shoulder Surfing 5.2.0

The installed camera mod rotates walking impulses into the camera direction and turns player yaw toward camera-relative walking. A scooter needs A/D steering and W/S throttle to retain their original meanings. The optional `shouldersurfing_plugin.json` entry point registers before Shoulder Surfing freezes its event bus. It cancels `ForceVanillaPlayerInputEvent` only for the local camera player who is the scooter's driver. Passengers and walking keep the camera mod's normal behavior.

`ScooterSteeringInput.mouseYaw(rider, scooterYaw)` uses the actual Shoulder Surfing camera yaw, with normal player yaw as the first-person fallback. Free look returns the scooter's yaw, making it a camera-only action. Common/server code does not resolve optional camera classes: the client and Shoulder Surfing references are isolated in guarded nested classes.

Parent integration: call this helper in enabled mouse steering, preserve the mouse toggle, and package the optional plugin JSON. Vanilla `KeyboardInput` makes A positive `leftImpulse`; positive world yaw turns left, so raw keyboard steering uses positive `xxa` rather than negating it.

## Backported Spears 1.8.0

The installed spear enchant is **`minecraft:lunge`**, separate from the scooter's own Lunge enchant. Its original enchant condition rejects every vehicle. Normal attacks use `PiercingWeapon.stab`; `TriggerStabEffectsC2SPacket.trigger` is the additional Better Combat route. The optional mixins add a bridge after both methods return and preserve their normal hit/damage logic.

The bridge requires a server player driving a scooter, a spear with its `PIERCING_WEAPON` component and actual Lunge enchant in the attacking hand, hunger above five, and neither swimming nor fall flying. The scooter's `spearLunge` method additionally checks driver, battery, rental and cooldown. Failed attempts consume no extra durability or hunger.

After acceptance, the bridge reuses the installed enchant's item damage, exhaustion and sound effects and omits its player impulse. It accepts only the installed compound effect shape with one of each known leaf and leaves unknown custom effect shapes untouched. The original condition's riding exclusion is replaced only for this scooter bridge; it does not make other mounts lunge. No speed boost is sustained by this component: the scooter applies the one-shot 100 km/h target and two-second taper.

Package `psychiatryk-scooter-spears.mixins.json` in unified mod metadata. The loading plugin applies the two hooks only when the optional `spears` mod is present. Whole-mod compilation requires the installed Shoulder Surfing and Backported Spears jars on its compiler classpath.

## Steering model audit

Both v31 and the current v32 candidate contain the same rig: front wheel Z = -0.4755584443, rear wheel Z = 0.4757271265, and renderer scale = 1.25. Horizontal wheelbase is **1.1891069635 blocks**. The authored model faces -Z; root transform is Y(180 - Minecraft yaw). Positive world yaw turns left, while positive authored Y steering turns right after that root transform. GPU/Flywheel animation time must therefore invert the effective world wheel angle. The flipped CPU mesh path already uses the corresponding inverse coordinate transform.

Flywheel model, trim and rocket attachments share its evaluated palette. Direct GPU attachments also use the direct renderer's palette. Grip targets evaluate the same animation but must use the same inverted time. The fallback grip already uses negative authored rotation, while the old unused fallback trim helper used positive authored rotation. All steering paths should consume one effective angle; body and camera lean should continue using the requested input.

`LivingEntity.tickRidden` is empty and its `travelRidden` calls it once, so the scooter's call to the superclass does not duplicate yaw application. Current Flywheel root Y(-yaw) followed by Y(180) also applies yaw once.

Component compilation is intermediate evidence. Runtime behavior and optional-mod/dedicated-server loading are gated by the parent's isolated QA fixture before release.
