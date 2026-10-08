# Server-owned grapple hooks

This overlay replaces the exact queued 3.0.9 `PeebGrapple` class in the 3.0.10 runtime build. The client-owned walking prediction and existing packets are unchanged. Only authoritative teammate/scooter impulses and validated block attachment receive extension events. Once this runtime is installed, editing server rules or KubeJS `server_scripts` requires no new client jar.

`config/psychiatryk-runtime/grapple.properties` is created on first server use. It is checked once per second, and an invalid edit leaves the previous valid snapshot active. `teammateImpulseScale` and `scooterImpulseScale` accept finite values from 0 to 2. `redstoneOnAttach` defaults to false; `redstoneCooldownTicks` accepts 4–200. The final script-adjusted force is still constrained by the native per-tick horizontal and total budgets, then by the existing entity collision checks. Incoming momentum is preserved.

These are regular NeoForge events on `NeoForge.EVENT_BUS` and can be handled from KubeJS `server_scripts` using `NativeEvents.onEvent` after the one-time runtime install. On KubeJS 2101.7.2-build.377, `kubejs reload server-scripts` hot-reloads edited listeners without a Minecraft data-pack reload, server restart, or client update. The command and listener replacement were verified on the isolated 69-mod fixture. A full `/reload` stalled the constrained fixture long enough to trip its 60-second watchdog, so use the script-only command for this workflow.

```js
const GrapplePullEvent = Java.loadClass('pl.aridlin.psychiatrykroles.runtime.grapple.GrapplePullEvent')
const GrappleHitBlockEvent = Java.loadClass('pl.aridlin.psychiatrykroles.runtime.grapple.GrappleHitBlockEvent')
NativeEvents.onEvent(GrapplePullEvent, event => {
  if (event.getKind() === 'teammate') {
    event.setImpulse(event.getImpulseX() * 0.75,
                     event.getImpulseY() * 0.75,
                     event.getImpulseZ() * 0.75)
  }
})

NativeEvents.onEvent(GrappleHitBlockEvent, event => {
  if (event.getRedstoneBlockId() === 'minecraft:lever') event.setActivateRedstone(true)
})
```

`GrapplePullEvent` exposes the owner, affected entity, kind (`teammate`, `teammate_scooter`, `scooter`), incoming and requested velocity, the two force budgets, bounded `setImpulse(x,y,z)`, `setRequestedVelocity(Vec3)`, and cancellation. Canceling omits this tick's added hook force. Invalid or oversized script impulses are rejected, and all accepted values are clamped to the native budgets.

`GrappleHitBlockEvent` is raised only after the native range, chunk, and two-ray block checks. Canceling prevents that attachment. Its anchor cannot be redirected by a script. `getRedstoneHit()` may be null; the candidate is a visible vanilla button or lever on the anchor face. An activation request still checks loaded chunk, player interaction permission, the normal NeoForge right-click event, current block type, and per-player cooldown. It invokes only the block's ordinary no-item interaction, never the held item's use or an arbitrary block mutation. The server-only settings or a script can request this activation.

Verification: `GrappleRulesTest` covers defaults, hot reload/last-good fallback, invalid values, finite motion, and native force budgets. The five modified/new Java source files compile against the exact queued 3.0.9 jar and NeoForge 1.21.1 SDK. Actual redstone interaction and KubeJS event delivery still require a staged in-game test before a live rollout.
