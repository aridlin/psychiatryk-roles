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
