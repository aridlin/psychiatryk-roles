# Scooter steering QA

This fixture drives only a new offline Prism clone and its integrated server.
It never launches/stops the main game, accesses production, or compiles changed
production source. The helper jar contains only test automation and read-only
observers of the actual submitted GemRender palette.

## Checks

- Exact candidate bytecode plus its authored GLB: left/right signs, quaternion
  animation mapping, wheelbase, chord curvature, stopped steering, finite limits.
- Normal 100-tick overspeed taper and one-shot 40-tick spear taper, tested on
  ground and in air with coast/throttle, braking and Flight/rocket cap transitions.
- Fourteen live phases: A/D at low, normal and 200 km/h; Shoulder Surfing A/D
  at low and normal with independently offset camera yaw; genuine Shoulder
  camera mouse steering in both directions; mouse steering disabled controls.
- The real rider remains mounted. Only the speed norm is held constant to isolate
  steering; actual rotation, movement heading, collisions and vehicle packets
  remain under the production implementation. Drift/enchantments are disabled.
- Fit an independent circle through actual positions, compare left/right mirror
  radii, chord curvature and the real GPU wheel palette. Pose-cache quantization
  is measured and explicitly included in the model angle tolerance.
- Server observes the same real vehicle. Its fitted radius and synchronized
  visible wheel angle must match the client over the steady window.
- Both actual grip API targets are compared to the submitted stem palette.

The installed pack uses `flywheel:off`, so the active scooter path is
`ScooterRenderer → DirectRenderer.submit`. The observer sees its actual palette
before submission. A retained Visual observer is also available; the default
runtime claim concerns the direct path.

## Reusable commands

Run from `~/Documents/Codex/2026-10-02/make` with the exact release
candidate and hash supplied by root:

```sh
python work/steering-v33-qa/unit.py --candidate outputs/steering-v33/psychiatryk_roles.jar
python work/steering-v33-qa/build.py --candidate outputs/steering-v33/psychiatryk_roles.jar
python work/steering-v33-qa/launch.py --candidate outputs/steering-v33/psychiatryk_roles.jar --sha256 EXACT_SHA --label unique-label
python work/steering-v33-qa/analyze.py outputs/steering-v33-qa/unique-label
```

The launcher checks candidate/helper hashes, uses software rendering in a
private Xvfb at 20 FPS, pins the full v32 mod stack, caps the run at 300 seconds,
and cleans up only its own process group. Original source-profile files are
hash-checked unchanged. The world uses copied fixture metadata converted to
flat generation; no player/production world regions are copied.

## Privacy

Publish only authored Java/Python sources, this README and sanitized reports
and fixture images. Exclude `prism-root-*`, private launcher/runtime logs,
copied configurations, account caches and compiled helper directories. The
local account cache is ephemeral, mode 0600 and removed in cleanup. The
launcher itself should remain private in public source bundles because it
encodes local profile/account handling.

## First live result

Candidate `0b38da10582b051317790e256ee8473ed18e7770ddea5bd75f6061c37633d694`
passed physical motion, camera controls and server synchronization but failed
actual wheel-angle agreement. In the direct renderer, equal-weight steering
and wheel animation blends halved steering against the other clip's rest pose:
28° became 14°, 5.78° became 2.84°. The failed control is preserved under
`outputs/steering-v33-qa/final`. It is not a release pass.

## Corrected final result

Candidate `fb6794deda990eb9df51e409728b308bfc5376d0fd5a01b1b59c82d81a68418b`
passes all 75 candidate/GLB/taper checks and all 158 live checks. The final
run collected 358 client, 356 integrated-server and 353 submitted-model
observations. Actual GPU steering now follows physical steering within the
measured pose-cache quantization, and both grip targets use the same submitted
stem pose. Measured mirror radii are about 2.223, 11.688 and 119.943 blocks at
low, normal and high speed. Shoulder keyboard radii match first person with
independently offset camera yaw; enabled mouse steering follows actual camera
yaw, while disabling it remains straight.

Detailed final evidence is `outputs/steering-v33-qa/corrected/analysis.json`.
`outputs/steering-v33/runtime-report.json` is the compact aggregate gate.
Source profile hashes remained unchanged and both disposable clients stopped.
Actual spear attack/cost/cooldown behavior is verified by the separate dedicated
server fixture; these steering reports claim only its pure 40-tick taper.
