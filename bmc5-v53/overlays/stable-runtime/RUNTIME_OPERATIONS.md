# Psychiatryk runtime: authoring and release boundary

The 3.0.10 client is the one-time platform install. Its optional, versioned
network envelopes and generic renderers stay fixed while the server owns the
data and gameplay rules. The initial 3.0.10 rollout requires a client update:
it registers the generic runtime block and item, so a 3.0.9 client may be
rejected by registry synchronization. After that install, data and script
changes within the frozen platform avoid another client restart. Never infer
permission or game state from a client's visual-ready acknowledgement.

| Change | Operator action | Client action |
| --- | --- | --- |
| Menus, controls, signed item variants, recipes, behavior graph | Atomically edit `config/psychiatryk-runtime.json`; the server watches it every two seconds, or run `/psychiatrykruntime reload` | Nothing; existing sessions are invalidated and new screens use the new revision |
| PNG/JSON visual assets (Peeb clips, scooter rules, generic item/block appearance) | Edit `config/psychiatryk-runtime-assets/`, then `/psychiatrykruntime assets` | Nothing; only changed hashes transfer, compile before Ready ACK, old visuals survive a rejected update |
| Per-player HUD scenes | Call `HudServer.replace`/`clear` from a server feature | Nothing; client caches prepared scene nodes and renders them in one bounded pass |
| Grapple force/redstone policy | Edit `config/psychiatryk-runtime/grapple.properties` | Nothing; valid edits apply within one second |
| KubeJS server event logic, including Peeb hooks | Edit `kubejs/server_scripts`, then `kubejs reload server-scripts` | Nothing; native event listeners were replaced in place on the tested KubeJS build |
| Existing KubeJS command callback logic | Keep the command registration stable and dispatch through a mutable startup-owned map; edit the server script, then `kubejs reload server-scripts` | Nothing; the tested callback changed immediately without rejoin |
| New command name or argument tree | Rebuild the dispatcher with full `/reload` or a server restart | Rejoin only if the server restarts; script-only reload does not re-register command nodes |
| New server-only Java hook implementation | Package through `ServerFeature` and the conservative server-only guard, then restart the server | Rejoin after restart; same frozen client binary |
| New native item/block/entity registry ID, packet kind, or renderer implementation | Build and distribute a new client version | Client game restart is required by Minecraft/NeoForge registration |

The generic runtime item and block registry entries can represent many
server-defined *variants* without registering a new native ID. This is useful
for sparse decorated blocks such as a KuKirin display stand. It is not a
replacement for mass building blocks: its bounded block-entity renderer draws
up to 16 cuboids per placed block and should be limited to sparse decoration.
The fixed `psychiatryk_runtime:runtime_item` can also use server-preloaded PNG
art. `items/runtime.json` maps up to 128 signed variant IDs to verified PNG
asset IDs. The client compiles that map at asset readiness and performs only a
variant-tag lookup during item rendering; unknown or unavailable art falls
back to a static icon. Names, lore, recipes, counts, actions, and artwork can
change through server data, but a new item registry ID or rendering technique
still needs a client binary. A client-side visual tag is not permission: the
server verifies its own variant signature before gameplay actions.
Gameplay remains server-authoritative. KubeJS and behavior graph commands run
on the server and must be written by trusted operators. Client asset JSON is
schema-validated by each feature compiler and cannot execute JavaScript or
arbitrary network requests. The asset protocol caps count, size, pixels, JSON
depth, transfer rate, and client request rate; see `ASSET_PROTOCOL.md`.
The `block_break` behavior hook runs only after the block state has changed
by the end of a server tick; a cancelled or protected break attempt does not
trigger it. Hooks that need the exact drops or block entity should use a
dedicated server-side event handler instead.

KubeJS build 377 was smoke-tested with a mutable Java map created in
`kubejs/startup_scripts`. A once-registered Brigadier callback looked up its
function from that map; replacing the function in `kubejs/server_scripts`
and running `kubejs reload server-scripts` changed the command's output from A
to B in the same server process. Its native event listener also changed in
place. Assigning a new `global` property from a server script was rejected by
KubeJS, so keep the map itself in a startup script and mutate its entries.
This proves the particular dispatch pattern, not that an arbitrary Java or
registry change can hot-swap. Full `/reload` was much slower in the constrained
fixture and is reserved for command-tree/data-pack changes.

This platform directly covers data-driven GUI changes, HUD layout, animated
Peeb clips, scooter transform corrections, and generic item/block appearances.
Spotify playback is **not** implemented by accepting a link: it would need a
separate authorized audio source/resolver and a client decoder. Likewise a
completely new renderer or native registry kind remains a client release.
Do not promise those as hot updates.

The join overlay reports manifest, local-cache verification, missing-file
transfer, feature preparation, and readiness. The first asset preparation
uses the full join timeline even when Minecraft has already installed the
world; later hot updates use a compact notice. Existing JSON and PNG
assets are retained under `cache/psychiatryk-runtime/assets/` by SHA-256;
missing assets transfer before gameplay renderers use them, avoiding first-use
download pauses. A failed update leaves the previous ready visuals active.

For release, preserve exact copies and SHA-256 values of the current server
JAR, `bmc5-patch.properties`, AutoModpack hosted JAR/feed, runtime JSON/assets,
KubeJS scripts, and `config/psychiatryk-runtime-secret.key`. The 32-byte
signature key must stay private and unchanged: replacing it invalidates
already-issued signed runtime variant items. Test the exact final JAR against
the exact live mod list, run a real client join/rejoin and cache-hit check,
then stage a rollback. Synthetic native fixtures and compile tests are useful
but do not prove the full BMC5 client renders or connects.

The official BMC5 v53 manifest recommends 10,720 MiB client RAM and ships an
enabled Ultra shader profile. On a 15 GiB laptop, do not launch the full client
and a local 3 GiB server together. Use a strong host or a controlled client
profile with shaders disabled, 6–8 chunk render distance, 4–6 chunk simulation
distance, and a 60 FPS cap for qualification before publishing performance
claims.
