# Dedicated observer source

`PeebDedicatedSmoke.java` is an authored test-only NeoForge mod, compiled with
Java 21 against the target addon and Minecraft 1.21.1 / NeoForge 21.1.250. Compile
it together with the sibling `elytra-loyalty-3.0.5/tests/EndgameLoyaltyQA.java`.
The source tree distributes no compiled helper or server fixture.

The private qualification used a fresh flat loopback world, the cached real
pack, a two-CPU/four-GiB JVM and normal save/stop. It creates real ServerPlayer,
Zombie and registered scooter objects and calls actual attachment/tick code.
Its player has a vanilla server listener with an observing send sink and
temporary player-list membership. That exercises packet registration, codec
roundtrips and server packet emission, but not native client transport or login.

Reflection reads private session state for assertions; it does not install a
fake grapple session. The observer's tests distinguish initial line of sight
from retained anchors, native target damage from hook cooldown, and scooter
velocity from rider velocity. Its isolated zero-armor target and reset vanilla
hurt immunity expose the grapple's own 4-damage and 20-tick cooldown behavior.

The sanitized receipt records 62 observer and 11 nested Loyalty checks. The
tested full artifact is EA181; all common/server/recipe entries are byte
identical in the 106f checkpoint. Minecraft's normal shutdown saved all worlds;
a known baseline executor required bounded JVM termination afterward.
