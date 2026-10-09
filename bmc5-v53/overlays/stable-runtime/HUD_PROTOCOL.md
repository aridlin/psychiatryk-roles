# Optional server-pushed HUD scenes (version 1)

The server owns up to eight named scenes per player. A scene contains up to 32 static nodes: `text`, `rect`, `progress`, `item`, or `image`. Across all scenes, a player is capped at 64 nodes, including at most eight item draws and sixteen image draws per frame. Positions and dimensions are normalized to the GUI screen (`0..1`), so the same scene scales to different GUI sizes. Replacing a scene ID redraws it; clearing removes it. No client action packet is accepted. Images refer only to preloaded PNG IDs from the optional asset manifest.

Example server API:

```java
HudServer.replace(player, new HudSchema.Scene("quest", List.of(
    new HudSchema.Node("caption", "text", .02, .04, 0, 0,
        "Find the old tower", 0xFFFFFFFF, 0, 0, null, null),
    new HudSchema.Node("progress", "progress", .02, .08, .22, .015,
        null, 0xFF88DD66, 0x88000000, .4, null, null)
)));
HudServer.clear(player, "quest");
```

The server validates IDs, coordinates, item registry keys, PNG availability, node counts, and a 16 KiB serialized message limit. Image scenes wait until the client's ready ACK for the current asset revision. The client parses once per update and precompiles text, item stacks, and texture references; rendering only scales and draws these prepared nodes. HUD state clears on disconnect. The messages use the existing optional runtime `Snapshot` payload with `channel="hud"`; they are sent only when both the runtime and the new asset channel are supported, so the prior runtime-only client never receives an unfamiliar HUD snapshot.

## Optional node expressions for future clients

A node may include `visibleWhen`, `xRule`, `yRule`, or `valueRule` as bounded JSON expression strings. `valueRule` applies only to a progress node. For example, a health bar can set `valueRule` to `{"op":"div","args":[{"var":"health"},{"var":"max_health"}]}`. A client with this extension evaluates it from local player state every frame; a 3.0.10 client ignores the extra fields and displays the static `value` fallback. The server can replace a scene while players remain connected. No new packet channel, game restart, or rejoin is needed once clients have this extension.

The HUD variable whitelist is `time` (seconds since the client HUD runtime initialized), `scene_time` (seconds since this scene was replaced), `speed` (horizontal blocks per tick), `yaw`, `pitch`, `airborne` (0 or 1), `vertical_speed`, `health`, `max_health`, `food`, `gui_width`, and `gui_height`. An unrelated scene update or an asset refresh preserves `scene_time`; replacing the same scene resets it. This uses the same compiled expression engine as scooter visuals, with a feature-specific variable whitelist. An expression cannot execute a command, access arbitrary client objects, load a class, or fetch a URL. The existing static coordinates remain the fallback for every optional expression. Animated positions and progress are clipped to the screen and 0–1 range respectively.

The server validates the expression string before sending: up to 1,024 characters per expression, 64 expression tree nodes per scene, and 128 across all active scenes per player, on top of the existing eight scenes, 64 HUD nodes, and 16 KiB message caps. The client compiles on scene or asset updates; render work evaluates at most 128 bounded tree nodes per frame with no per-node allocation. Updating a scene is atomic: a rejected expression leaves the prior scene active. This source extension is not part of the live 3.0.10 JAR; its actual in-game rendering and frame time still need a client/server test before release.

The next source layer also accepts bounded server-owned numeric and boolean variables named `signal.<id>` in HUD expressions. See [SIGNAL_PROTOCOL.md](SIGNAL_PROTOCOL.md) for the capability handshake, typed deltas, limits, and the static fallback sent to older clients.

Integration in existing files:

```java
// StableRuntime constructor
NeoForge.EVENT_BUS.register(new HudServer());

// RuntimeClient client setup, in place of the bare RuntimeClient::receive assignment
RuntimeNetwork.receiver = raw -> {
    if (!HudClient.tryAccept(raw)) RuntimeClient.receive(raw);
};
```

`HudClientSetup` is discovered on `Dist.CLIENT` and registers an asset-ready callback to refresh image references after every preload or hot update. The schema test passes 24 checks under a 256 MB runtime heap. The entire current stable-runtime source compiles under a 384 MB compiler heap. Rendering, reconnect cleanup, and older-client compatibility still need an actual in-game check before release.
