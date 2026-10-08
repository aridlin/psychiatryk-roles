# Roles 3.0.9 source overlay

Requires JDK 21, a NeoForge 1.21.1 development classpath and the exact loader-fixed 3.0.8 baseline:
`56cc13966c2ed202ff54ac9e0b401fce32325842d93fff2be22b1ee14de9b614`.

Run `python3 build.py --base /path/to/psychiatryk_roles-3.0.8-bmc5-loaderfix.jar --sdk-classpath /path/to/classpath.txt --output /path/to/build`.

Seven ordered overlays are rebuilt from authored source and bound to exact runtime hashes. Method transplants preserve unrelated deployed methods and inner classes. The builder verifies byte-identical reconstruction of the final combined JAR. All six mod versions become 3.0.9, and the two new client mixin registrations occur exactly once.

- Shared admin handling synchronization and a high-speed steering floor.
- Authoritative same-party teammate pull.
- Pause/resume of vanilla background music while custom nearby music is audible.
- Narrow EMF options-label compatibility.
- Supported ModLauncher bytecode-provider calls for JEI, Flywheel and EMF.
- Peeb movement-facing whole-body turning, attached animated eyes and tusk dye.
- Collision-safe partial corner steps when the existing admin grapple-step toggle is enabled.

This export contains Java sources, authored fixtures, resources for the two new mixin configurations and a portable builder. It excludes compiled classes, prior game assets, original-game inspection/decompilation files, third-party JARs, music catalogs, credentials, assignments and production receipts. Headless fixtures do not establish live visual or audible confirmation.
