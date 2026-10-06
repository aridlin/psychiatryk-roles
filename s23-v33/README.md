# Goplanska S23 v33 — scooter steering

Scooter-control update on the exact [v32 baseline](../s23-v32/README.md).
Keyboard steering follows the requested direction, with curvature-bounded
low-speed turning instead of spinning in place. The previous high-speed yaw
envelope is preserved and the steering model stays within ±28 degrees.
Third-person input is corrected. Overspeed tapers linearly toward cruise over
five seconds. Direct model animation layers retain the full steering angle
and share the same hand/handlebar pose. Soul Speed raises the terrain speed cap
once instead of compounding velocity each tick. A spear with its own Lunge enchantment gives a one-shot 100 km/h
boost with a 40-tick taper. Mouse steering and the other v32 features remain. build-report.json lists the exact changed steering classes;
all unrelated existing unified-JAR entries are byte-identical to v32. No separate mod or world-save format is added or removed; the exact helper
classes and optional compatibility resources are listed in the build proof.

runtime-report.json identifies the tested candidate and actual checks. These
checks do not cover every terrain/network/input situation or establish a new
production FPS measurement. The v32 download and per-file activation backup
remain available for rollback. Existing public component source/history is
retained, with current steering source and synthetic helpers added. Private
launcher/account caches, worlds, screenshots, logs and profiles are excluded.

A verified production activation receipt is included in steering-deployment.json; consult its fields for the checks actually performed.
