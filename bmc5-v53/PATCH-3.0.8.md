# Roles 3.0.8 source delta

Compact song cards beside Xaero's actual minimap bounds; sharp music menu;
vanilla dye support for Peeb and corrected eye/body geometry; the existing
Ordynator, Pacjent, Konsultant and Contractor role framework is restored.
BMC survival item/recipe restrictions remain. Old-world, build-transfer and
season modules remain disabled. Optional grounded/midair Peeb stepping is
disabled by default and can be enabled by an administrator.
Grapple initiation uses synchronized player yaw and a bounded attachment grace
while retaining the configured target range and sustained grapple physics.
Shared music streaming has bounded retries and authenticated server-packet
access. A conditional client mixin bridges legacy JEI unit transfers.
A second conditional client mixin backports the exact legacy Flywheel range
clear repair, skipping newer/fixed or unrecognized class variants.

Merge alongside the reviewed 3.0.7 source tree. No JARs, private catalogs,
historical fixtures, artwork or game assets are distributed. Supply the exact
external ff773260 Roles3.0.7 checkpoint and Java21 Minecraft1.21.1 / NeoForge
21.1.250 classpath including ASM9.10.1, then run:

    python build.py --base /path/base.jar \
      --classpath-file /path/classpath.txt --output-dir /path/fresh-build

The builder compiles only reviewed authored groups, preserves unrelated
deployed Mesh/Renderer bytecode with the included narrow method transplant,
verifies every runtime entry and reproduces the complete candidate hash.
No game launch, deployment, network request or Git write is performed.
