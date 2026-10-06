# Goplanska S23 v34 — scooter direction and reversing

Control/model correction on the exact [v33 baseline](../s23-v33/README.md).
A/D use Minecraft's actual camera basis. The front steering assembly, trim and
grips share a pose tied to the rolling turn radius. The chassis stays fixed
at rest while the handlebars may turn. S brakes forward motion, then reverses
after stopping; backward motion reverses yaw while preserving wheel direction.
Existing mouse steering, Shoulder Surfing, body lean, spear Lunge and overspeed
features remain. build-report.json declares every changed class and model asset;
all unrelated unified-JAR entries and all other mods are preserved from v33.

runtime-report.json names the exact tested candidate and control/model cases.
It does not establish universal terrain/network behavior or production FPS.
The v33 downloads and per-file activation backup remain available for rollback.
Only authored source, model resources and synthetic helpers/reports are included.
The compatibility-runtime-v33-report.json remains historical evidence of the
unchanged spear bridge. The v33 direction assertion used an incorrect left/right
basis; v34's independently verified Minecraft-camera basis is authoritative.
Private account caches, worlds, decompilation, logs and server/profile exports
are filtered; Python helpers are parsed before publication.

Production deployment is not asserted by this preparation. runtime-report.json states the exact isolated steering tests performed.

## Production status

The tested v34 files are staged. The two-player restart ballot was rejected
with zero yes and two no votes, so no production files were activated and no
restart was performed. Production AutoModpack, the current website download
links and the existing Prism profile still select v33. Versioned v34 archives
and authored source are prepared. See steering-staging.json.
