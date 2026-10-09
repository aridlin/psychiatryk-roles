# Optional runtime asset preload (version 1)

Place `.png` or `.json` files below `config/psychiatryk-runtime-assets/`, for example:

- `ui/poker/card.png` is asset ID `ui/poker/card`.
- `peeb/clips.json` is asset ID `peeb/clips`.
- `scooter/rules.json` is asset ID `scooter/rules`.
- `items/runtime.json` is asset ID `items/runtime` and maps generic item
  variant IDs to PNG IDs such as `items/token`.

IDs use lowercase letters, digits, `_`, `-`, and up to four `/`-separated path segments. The client never uses IDs as local filenames. A `.png` and `.json` with the same ID are rejected.

On join, the server sends a versioned manifest with each asset's type, SHA-256, byte count, and (for PNG) dimensions. The server decodes each bounded PNG before publishing a new snapshot. The client checks at most two cache entries per client tick, then requests only missing entries and receives 16 KiB chunks (at most four chunks per server tick globally). It validates complete SHA-256 and PNG dimensions or strict UTF-8 JSON syntax before a render-thread PNG registration or JSON handoff. New assets are staged while the prior ready snapshot remains in use. Feature callbacks run after all entries are preloaded and before Ready ACK. A failed callback rejects the candidate, keeps the previous textures and JSON active, and sends no ACK. No resource-pack reload, URL request, or first-use transfer occurs.

An asset reload with an identical manifest revision keeps ready clients and in-flight transfers intact, so an unrelated server data edit does not recompile visual data or push another manifest to them. A transfer that times out receives a fresh manifest automatically, with at most two retries per update. The client keeps the candidate active long enough to accept that resend. After all three attempts fail, an operator can retry with `/psychiatrykruntime assets` without requiring a reconnect.

Limits: 32 files, 256 directory entries scanned, 2 MiB total transfer, 128 KiB per PNG, 64 KiB per JSON, PNG sides at most 512 pixels, four million total decoded PNG pixels, JSON nesting at most 16, client timeout 40 seconds, server timeout 30 seconds, at most two automatic timeout retries, and eight client Request/Ready packets per player per second before JSON parsing. The disk cache is pruned to 8 MiB, retaining current manifest hashes. Asset reload is atomic at the manifest level: a bad file leaves the previous live snapshot unchanged.

Integration in `StableRuntime` constructor:

```java
bus.addListener(AssetNetwork::register);
NeoForge.EVENT_BUS.register(new AssetServer());
```

`AssetClientSetup` is discovered automatically on `Dist.CLIENT`; no `RuntimeClient` edit is needed. For hot reload, call `AssetServer.reload(server)` from an operator command after a successful server-side config update. `AssetServer.ready(player)` gates server features that require the latest asset revision. `AssetServer.readyAck` calls `RuntimeServer.assetsReady(player)` exactly when it first accepts an ACK, allowing a pending visual menu to open.

Only images visible to a player delay that player's menu. The wait is capped at five seconds; if art fails or remains in transfer, the menu opens with the client's existing loading placeholder and later resolves when assets become ready. A runtime config revision clears stale pending menu opens. Ready ACK is a visual timing hint from the client, never an authorization decision.

Register client data compilers during client setup, before joining:

```java
AssetClient.onReady(() -> {
    AssetClient.json("peeb/clips").ifPresent(clips -> compileAndValidateClips(clips));
    AssetClient.json("scooter/rules").ifPresent(rules -> compileAndValidateRules(rules));
});
```

On every join and hot manifest update, hooks run on the render thread. Throwing a `RuntimeException` or `LinkageError` rejects the manifest. `AssetClient.texture(id)`, `AssetClient.dimensions(id)` (`Size.width()/height()`), and `AssetClient.json(id)` are available inside hooks and after `AssetClient.ready()` becomes true. The asset layer validates JSON syntax only; each feature engine must validate its own schema and restrict supported operations.

The optional join screen can subscribe during client setup with `AssetClient.onProgress(progress -> ...)`. Each immutable `Progress` snapshot includes the phase (`checking`, `transferring`, `verifying`, `preparing`, `ready`, `failed`, or `disconnected`), aggregate byte and file counts, the active ID, whether previous assets remain usable, and per-file `ProgressItem` status (`waiting`, `queued`, `transferring`, `verifying`, `cached`, or `verified`). Updates occur only on state changes, so the screen should animate between them on its render tick and detach on `disconnected`.

Evidence: the entire current stable-runtime source compiled under Java 21 with a 384 MB compiler heap. `AssetProtocolTest` passes 37 checks, `AssetMenuGateTest` passes 11, `AssetRetryTest` passes 12, `AssetRateTest` passes 13, and `AssetPrepQueueTest` passes 34 with a 256 MB runtime heap. The tests cover manifest tampering, path traversal, duplicate IDs, oversized/deep/malformed data, strict UTF-8, bounded network chunks, filesystem scanning, truncated PNG rejection, noisy directory limits, symlink handling, visible-only menu gating, the fallback deadline, bounded timeout resend decisions, two cache checks per client tick, and the shared pre-parse rate budget. A real client join, cache hit/miss, DynamicTexture rendering, hot reload, and unchanged-client reconnect remain required before release.
