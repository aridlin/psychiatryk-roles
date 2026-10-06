# Save I/O overlay v32

## v32 save I/O
Player NBT and Sable saves run on ordered background workers after detached
snapshots are captured on the server thread. Existing gameplay and rendering
features from v31 are preserved. Vanilla .dat_old replacement and Sable DSYNC /
force(true) durability are retained; this does not remove filesystem flushes.
Failed asynchronous snapshots remain queued in order for retry. Queue limits
apply before accepting new work. Persistent storage failures are reported and
can reject further saves; an unavailable disk cannot be made reliable by this
update. Player loads, Sable reads/new allocations, explicit flushed saves and
shutdown barriers may still wait. This reduces ordinary autosave stalls, not
the hosting disk's underlying latency. An abrupt process kill can still lose
work that has not completed, as with other asynchronous Minecraft saves.
Client command /chams scooter off hides bound-scooter chams and dots only for
you; /chams scooter on restores them. This preference persists per client and
does not change other chams categories or server ownership.
Flashback skips unnecessary idle wire copies for PLAY payloads already excluded
from its pre-roll snapshots. Recording, paused recording, configuration packets
and snapshot-eligible packets retain their capture behavior. Snapshot reflection
lookups are cached per class; current packet values and pre-roll state stay live.
Sound Physics tracing uses 24 environment rays, 2 bounces and 8 occlusion rays
(previously 32 / 4 / 16). Moving-sound evaluation remains enabled every 5 ticks;
wall occlusion, direction evaluation and existing smoothing are unchanged.
Only these three property values are tuned; Prism retains other user settings
and backs up each existing root/managed properties file before atomic updates.
Ordinary villager trades use their stored vanilla baseline costs and limits.
Wieśniuk has unlimited stock; its purchases preserve ordinary stock counters
and avoid sentinel arithmetic corrupting demand. Existing offers, enchantments,
discounts and demand stay intact. Unknown historical random baselines cannot
be reconstructed exactly without rerolling and are preserved.
Level-II scooter Frost Walker extends its path from existing crust/ice and
prepares a continuous bounded strip up to 12 blocks ahead. Conversions require
a loaded neighbor halo, so planning the strip does not force chunk generation.
PointBlank shots fired while mounted ignore the shooter's own scooter and
co-passenger in aim selection, server validation and slow-projectile collisions.
Outside targets, other vehicles and explosive splash are unchanged. This fixes
own-mount interception; it is not evidence of direct mount-to-shooter damage.
PointBlank's mask, lens and glow pass cleanup restores neutral stencil state:
ALWAYS comparison, KEEP operations and all stencil write bits enabled. Both the
OpenGL state cache and actual state are synchronized. This prevents a later
portal world pass from re-enabling a leftover scope-only comparison. Scopes
remain enabled. Runtime reports state the exact render phases verified; they
do not establish a universal fix for every shader or scoped weapon.

Compile the Java 21 source against the exact NeoForge 21.1.252 / Sable 2.0.5 stack. The local classpath.txt is deliberately excluded. build_save_io_candidate.py overlays the compiled classes and mixin registration onto the unchanged v31 unified JAR.
