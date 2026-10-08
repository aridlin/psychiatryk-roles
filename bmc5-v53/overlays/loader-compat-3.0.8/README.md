# Roles 3.0.8 client-loader repair

The original compatibility plugins called Sponge's ModLauncher bytecode provider with an unsupported flag. The native provider throws before the client can finish loading. The repair uses the supported `getClassNode(name, true, 0)` call and skips transformation of its own plugin during provider lookup.

The 3.0.8 overlay replaces exactly `JeiTransferCompatPlugin` and `FlywheelLegacyCompatPlugin`. All other addon class, resource, recipe, protocol and TOML bytes remain identical to the original 3.0.8 JAR. The included EMF plugin is the corresponding repair for the separate, not-yet-active 3.0.9 candidate; it is not inserted into 3.0.8.

## Build

Use Java 21, the exact original 3.0.8 JAR (SHA-256 in `native-proof.json`) and a local classpath file containing the matching NeoForge/Minecraft/ASM/Sponge dependencies:

```sh
python build.py --baseline /absolute/path/psychiatryk_roles-3.0.8-bmc5.jar --classpath-file /absolute/path/classpath.txt --out /absolute/path/fresh-output
```

This reproduces and verifies the exact corrected 3.0.8 candidate and two-class overlay. It does not launch Minecraft or make network/deployment changes.

## Native fixtures

The fixture sources exercise the installed Sponge transformer, actual ModLauncher bytecode-provider boundary and real JEI/Flywheel/EMF target classes. `ModLauncherBoundaryTest` reproduces the original unsupported false-flag failure, verifies the supported true/flags-zero path and checks self-processing exclusion. Native fixtures cover legacy, updated, absent and structurally unknown dependencies and server-side bypass. They use bounded file-only transformer transport, not a full game-layer launch.

`native-proof.json` records the original fixture results and source fingerprints. It is sanitized historical evidence, not a claim that the portable build reran every fixture or that a user-visible game launch succeeded. Actual client startup is separately verified before publication. No dependency JARs, compiled classes, world data, private logs, credentials, account/roster data or music catalog are included.
