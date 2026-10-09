# Optional server signals (version 1)

`SignalServer` exposes per-player, server-owned values for reusable HUD logic:

```java
SignalServer.setNumber(player, "quest.progress", .6);
SignalServer.setBoolean(player, "quest.ready", true);
SignalServer.setText(player, "quest.label", "Find the exit");
SignalServer.clear(player, "quest.ready");
SignalServer.clearAll(player);
```

Names use lowercase letters, digits, `_`, `.`, and `-`, up to 32 characters. A player may hold at most 16 values. Numbers must be finite and within ±1,000,000; text is limited to 48 characters and excludes control characters. An attempted 17th active value or invalid value is rejected without changing state. State is discarded on logout. Call this API on the server thread.

New clients advertise `typed_state_v1` through the optional `psychiatryk_roles:runtime_capabilities` serverbound packet after channel negotiation. The server sends signal data only after that handshake. Old 3.0.10 clients never advertise the capability and never receive a signal snapshot. Menus and existing HUD scenes keep their original runtime envelope. A HUD scene that references a signal is sent to an old client with only its signal-dependent expression fields removed; the declared static position, visibility, and progress values remain. Once a new client handshakes, its full scenes are resent.

Data uses the existing optional `psychiatryk_roles:runtime_snapshot` packet with `channel: "signal"`. A reset clears the client's map, followed by changed-value `delta` messages. A delta carries typed `number`, `boolean`, or `text` values; `clear` is a tombstone for one name. Unchanged values are not resent. A reset precedes replay after a handshake. The client also clears all signal values when its connection changes. Incoming deltas are applied atomically and cannot grow the client map beyond 16 values.

Each message has at most 16 changes and at most 2,048 UTF-8 bytes, including JSON escaping. Larger current state is split across packets. Delivery is at most one packet every four server ticks and at most five packets in any rolling second for each player. The capability packet is at most 256 bytes. All packets have bounded parsing and no client-originated signal value operation.

HUD expressions can use `{"var":"signal.quest.progress"}` and `{"var":"signal.quest.ready"}`. Number values evaluate as numbers, booleans as 1 or 0, and absent or text values as 0. The existing compiled expression budget applies; this does not evaluate source code or expose arbitrary client fields. For a progress node, retain a static `value` as the fallback for an old client or an absent signal.

The source and focused tests qualify the protocol and compilation, but this branch has not launched a game client or verified an in-game frame, reconnect, or release JAR. A client/server session is still required before release.
