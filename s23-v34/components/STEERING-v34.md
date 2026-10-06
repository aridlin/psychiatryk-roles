## v34 scooter direction and reversing
Keyboard A/D follow Minecraft's actual camera basis. The visible steering
assembly, grips and trim use the same pose, and the front wheel follows the
rolling turn radius. The chassis does not pivot at rest; the handlebars can.
S brakes forward motion first, then reverses after stopping. Backward rolling
reverses chassis yaw while preserving the requested handlebar direction.
Mouse steering, Shoulder Surfing, body lean, overspeed taper and spear Lunge
remain. Runtime reports describe only the actual control/model cases exercised.
Every unrelated existing unified-JAR entry and every other mod is preserved
from v33. Keep the v33 pack and the activation backup for rollback.
The earlier v33 direction assertion used an incorrect left/right basis; v34's
Minecraft-camera basis checks are the authoritative direction evidence.

The spear compatibility report is historical v33 evidence. Those bridge classes are byte-identical in v34; v34 runtime-report.json covers the changed scooter core/control/model cases.
