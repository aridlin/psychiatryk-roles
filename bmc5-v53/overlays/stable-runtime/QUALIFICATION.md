# Stable runtime qualification — 9 October 2026

The tested 3.0.10 candidate is `build/psychiatryk_roles-3.0.10-bmc5.jar`,
SHA-256 **`3d4842404a38b205a3ca0541ae6ba5a4b4423ef0ecc46fcc094baec5f7970964`**.
The staged retry candidate and exact full-pack fixture copy have the same hash.
The queued 3.0.9 baseline is
`a513a1e338f0651648dfa32ecf9f3049daab312c2304183083337ecbc43572de`.
All 757 baseline JAR entries remain present; 16 existing entries changed and
96 entries were added. The Peeb client/server split, asset retry, paced client
cache preparation, and server-owned runtime services are included.

## Completed qualification on the exact hash

- All **11** Java test mains compiled with a 256 MiB compiler heap and passed
  with 256 MiB runtime heaps. `ProtocolTest` passed 4,038 checks;
  `AssetProtocolTest` 37; `AssetRetryTest` 12; and the new
  `AssetPrepQueueTest` 34. Per-test results are in
  `build/tests-final-3d48/results.json`.
- `python tests/GuardTest.py` passed **29/29** client-freeze checks. A
  same-shape `AssetServer` implementation change was accepted despite a
  server-owned `HudServer` reference; true `HudClient` references and
  client/protocol mutations were rejected. This gate does not make arbitrary
  Java changes safe without a client update.
- A fresh NeoForge 21.1.252 native fixture using the exact candidate reached
  `Done`, printed `[Psychiatryk Runtime] schema=1 ... menus=4 variants=1`,
  passed **96** registration, recipe, configuration, session-replay, menu,
  permission, rate-limit, and live-file-watcher checks, wrote its new `PASS`
  marker, and exited 0. Private log:
  `work/migration/private_release/workstations-20261008/runtime-smoke-final-3d48/native-run.log`.
- The authenticated live `mods/` archive was verified at SHA-256
  `19049e1e0d4d759c5a01206a4a115ba40cb3055738f6eb7077a56e50cb5534f4`.
  It has 296 active and 61 disabled JARs. The exact-pack fixture used 295
  unchanged active JARs, this candidate, pinned KubeJS build 377, and Rhino
  build 85: **298** loadable JARs, with no duplicate top-level mod IDs.
- The first exact-pack run reached both `Done` and the Psychiatryk runtime
  marker, then its 600-second safety timer terminated it (exit 72). A second
  run on the generated world, retaining `-Xmx3G`, a 6.5 GB address-space
  limit, two CPUs, loopback binding, and host RAM guards, reached
  **`Done (69.894s)` at 01:05:46**, printed the runtime marker, and loaded
  **1/1 KubeJS startup scripts and 1/1 server scripts** without script errors.
  Console `stop` saved the world and exited **0**. There was no crash report,
  OOM, watchdog, or memory-guard stop. Private evidence:
  `work/migration/private_release/workstations-20261008/full-live-smoke-3d48/smoke-console-second.log`
  and `fixture-proof.json` alongside it.
- In the isolated shell, an unchanged `luna_minecraft`/e4mc background
  thread could not load its native cache file under `/home/aridlin/.e4mc_cache`.
  The server completed startup and clean shutdown despite this fixture-side
  warning. Unchanged live mods also emitted tag, recipe, and client-class
  warnings.

Earlier KubeJS/Rhino testing on a smaller 69-JAR fixture demonstrated that
`kubejs reload server-scripts` can replace a native event handler and a
startup-owned mutable command callback without a client restart. The full
298-JAR fixture proves KubeJS script loading, not that every gameplay change
can be hot swapped. The private KubeJS and full-pack qualification records
retain the exact hashes and command-level evidence.

## Release checks still open

1. Verify an actual client authentication and world spawn with the exact
   released pack, then menu rendering, blur, asset cache miss/hit, reconnect,
   and a subsequent server-only update with the client binary pinned. A
   server status ping or synthetic player is insufficient.
2. Preserve and hash the current live JAR, AutoModpack hosted JAR/feed,
   scheduled patch properties, runtime configuration/assets/scripts, and
   private signing key; keep a tested rollback before changing live files.
3. Stage the initial 3.0.10 **server and client** update together. New native
   runtime item/block registry entries require a one-time client update; an
   existing 3.0.9 client cannot be assumed compatible. Only later changes
   passing `server_only_guard.py` and a real unchanged-client join may leave
   that client binary pinned.
4. After a controlled restart, verify live logs, the exact world/config,
   server health, a real player join, and the AutoModpack feed hash. The
   isolated fixture proves pack compatibility and startup, not deployment.

The user's fallback testing window ends at 07:30, with 08:30 as the hard
final time. If a real-client proof remains unavailable, report that limit
precisely rather than claiming full stability. The existing Modrinth draft is
[Psychiatryk](https://modrinth.com/project/psychiatryk-aridlin); use actual
screenshots showing aridlin rather than invented client renders or a duplicate
project.
