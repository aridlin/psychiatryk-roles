# S23 v32

Player NBT and Sable save I/O now uses ordered background workers. Detached
snapshots are captured on the server thread; the worker does not read live
players or mutable physics assemblies. Existing v31 client/server features and
unrelated mod entries are preserved; build-report.json lists every deliberate
bytecode/resource change. `/chams scooter off` hides the owner's
bound-scooter chams and top dots for that client; `on` restores them. Preferences
persist without changing other highlight categories or server ownership.

Client performance overlays skip expensive idle Flashback wire copies only for
PLAY payloads already rejected by its pre-roll snapshot filter. Active and
paused recording, configuration packets and snapshot-eligible packets retain
their capture behavior. Reflection discovery for packet snapshots is cached
per class, including absent fields/methods; live packet values are not cached.
This preserves pre-roll state while reducing avoidable allocations.

Client Sound Physics tracing budgets are 24 environment rays, 2 reflection
bounces and 8 occlusion rays (v31: 32 / 4 / 16). Moving sounds still update every
5 ticks; wall occlusion, directional evaluation and smoothing are unchanged.
The Prism update backs up each existing root/managed properties file and changes
only those three key lines, preserving the player's other sound preferences.

Ordinary villager prices and limits use their stored vanilla baseline. Wieśniuk
stock is unlimited without consuming ordinary stock or overflowing demand on
restock. Existing offer results, enchantments, gossip and demand are preserved.
Unknown historical random baselines stay intact rather than being rerolled.
Level-II scooter Frost Walker extends from existing crust and ice, and prepares
a continuous strip up to 12 blocks ahead. Its loaded-neighbor halo prevents
conversions from forcing chunk generation at the edge of loaded terrain.

PointBlank shots fired from a scooter ignore that same scooter and co-passenger
in aim selection, supplied-target validation and slow-projectile collision.
Outside targets, parked scooters, other vehicles and explosive splash behavior
remain. The actual dedicated-server fixture passes 14 checks. This fixes
own-mount interception; it does not establish direct mount-to-shooter damage.

PointBlank's mask, lens and glow pass cleanup restores neutral stencil state:
ALWAYS comparison, KEEP operations and all write bits enabled, synchronized in
both the OpenGL cache and actual state. The portal renderer enables the test
before opaque terrain without resetting its comparison; a leftover scope-only
EQUAL comparison against a cleared zero buffer rejected that terrain. Scopes
remain enabled. Consult tests/pointblank-stencil-v32/runtime-report.json and the
QA report for the actual tested render phases. Those results are not a universal
guarantee for every shader pack or weapon. Only our authored mixins and synthetic
QA are published;
PointBlank/Veil decompilation and private launcher state are excluded.

Vanilla .dat_old replacement and Sable DSYNC / force(true) operations remain.
Failures retain asynchronous snapshots in order for retry, with bounded queues
and visible errors. Loads, new allocations, explicit flushed saves and shutdown
barriers may still wait. The update reduces ordinary autosave stalls; it does
not repair the hosting disk. A process killed before queued work completes can
still lose that unfinished work. Persistent disk errors cannot be hidden or
made safe by queueing indefinitely.

runtime-report.json records the actual isolated cases and the tested candidate
checksum; build-report.json proves preservation of the existing mod entries.
The test-only sources include controlled disk-delay/failure injections and
synthetic FakePlayers. No production world/player data or credentials are
published. Private UUIDs and local home paths are scrubbed from JSON reports.

PlayerEvent.SaveToFile still fires on the server thread after enqueueing the
player snapshot, so future add-ons must not assume .dat has completed at that
event. No SaveToFile listener was found in the recursively scanned installed
v31 mod stack. Explicit load/flush barriers provide completed-save ordering.

An actual deployment receipt is included as save-io-deployment.json. It records activation; consult its fields for the checks actually performed.
