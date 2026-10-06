# Focused scooter control and attachment QA

This fixture followed a failure in v33's left/right semantic test. Vanilla Minecraft's yaw-zero camera faces +Z and its real camera right basis is −X. Positive yaw turns right. The corrected checks measure actual camera projection, real A/D/W/S input, client/server movement and submitted GPU palettes independently of implementation sign labels.

The normal fixture runs 21 phases: genuinely stationary neutral/A/D; HUD-zero residual speed; full walls blocking W+A/W+D; snow, ice and water at rest; first-person and Shoulder Surfing A/D; Shoulder mouse left/right; S from rest; S braking forward before reverse; W braking reverse before forward; W+S brake only; and A/D while reversing. A 24-tick neutral settling period resets earlier input and terrain. One initial-condition seed starts each measured phase. Measured phases never inject velocity or steer/yaw.

The targeted closeup mode runs only neutral/full A/full D and the forward A/D controls. It looks down at the actual stem/deck interface and records the exact render partial-tick wheel value. The private launcher uses a fresh offline integrated world, full client pack, llvmpipe and Xvfb, then stops its own clone. The launcher is intentionally excluded from publication because its private setup copies the local ephemeral launcher account cache.

## Reusable safe checks

```sh
python work/steering-v34-qa/unit.py --candidate outputs/steering-v34/psychiatryk_roles.jar
python work/steering-v34-qa/build.py --candidate outputs/steering-v34/psychiatryk_roles.jar
python work/steering-v34-qa/analyze.py outputs/steering-v34-qa/v34-final
python work/steering-v34-qa/analyze.py outputs/steering-v34-qa/v34-closeup --closeup
```

`unit.py` puts the actual candidate first on the runtime classpath and compiles only the QA class. It never compiles or shadows production Handling.

## Geometry measurements

`ScooterDrawObserver` brackets the real renderer. `DirectDrawObserver` copies the actual submitted body/pivot/stem/front/rear wheel matrices. The physical rolling direction is perpendicular to the horizontal wheel axle projection: `atan2(frontwheel.m02, frontwheel.m00)` in the authored model's renderer basis. The legacy raw `model_wheel_angle` field is the projected authored −Z vector; its pitch/camber makes it unsuitable for this physical steering comparison. The analyzer derives physical rolling angle from the copied frontwheel palette.

Both points of the raked headset axis must stay at the body's original seam rings. An independent geometry fixture measures actual brace/deck triangle-surface distances, hub positions and grips. Frame steering uses the exact sampled partial-tick value in closeups. The earlier broad report bounds interpolation between its two observed endpoints, with the separately measured pose-cache quantization bound of 0.24 degrees. This prevents an acceleration ramp from being mistaken for rendering lag.

Server control packets are sent every four ticks. Exact sampled client wheel values must appear remotely within that interval plus one processing/sampling tick. No generic angle tolerance hides a reversed wheel or stale sequence.

## Final proof and limits

Frozen candidate SHA256: `965392449a2b91432712d4f844b078074d6b660a3f3c00e4915e5f5904dc0a20`.

- Broad actual control analysis: 145/145.
- Exact-partial closeup analysis: 40/40.
- Independent submitted-palette mesh continuity: 32/32.
- Pure brake/reverse/deadband outcomes: 98/98.
- Compiled geometry helper: 231/231.

Reports and images are under `outputs/steering-v34-qa/`; aggregate under `outputs/steering-v34/runtime-report.json`. Raw evidence is preserved. Source test-profile hashes were unchanged after both launches, and no private clone Java process remained.

These checks cover the actual full-pack integrated-server/direct-render path and Shoulder Surfing controls. They do not claim production deployment, long-duration high-speed testing, sound retesting, or an unchanged live main-process PID. No main instance or production server was launched, stopped or changed by this fixture.
