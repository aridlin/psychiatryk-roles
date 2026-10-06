# Scooter steering rig continuity audit

The original steering mesh incorrectly contained the fixed deck-to-headset brace and rotated around the front wheel hub on a vertical axis. A full steering angle moved that brace away from the deck. The corrected asset keeps the 108 brace triangles in the fixed body and rotates the fork, stem and front wheel around the measured raked steering-tube axis.

The source patcher is `../generators/repair_scooter_steering_rig.py`. Supply the baseline model asset directory and a separate output directory. It preserves all 9,126 triangles, original UVs, normals, materials and texture references. The wheel remains local to its own hub, beneath the steering node. It does not add draw passes or geometry. Input model files are not bundled with this audit.

`ScooterSteeringRig` maps the requested projected wheel yaw to the raked axis angle. Full ±28° projected steering requires ±28.9514° rotation around the measured axis. The retained visual, direct renderer and grip calculations use the same animation time; the fallback renderer applies the equivalent transform to its Y/Z-flipped vertices.

## Evidence

- `continuity_qa.py`: checks preserved triangle/UV/normal data, nearest point-to-triangle distances at the brace/deck contact, the raked headset axis, full projected steering and the fallback transform. NumPy is required.
- `compiled-qa/SteeringRigQA.java`: tests the exact candidate helper and G2Mesh bytecode using an isolated cached Minecraft resource fixture, without launching Minecraft. Java 21.0.7 passed 231 checks, including every integer steering angle from −28° to +28°.
- `runtime_continuity_qa.py`: compares full bone palettes actually submitted by the isolated client renderer against real model triangles. It checks the fixed brace, headset axis, front-wheel hub/axle and grip targets. Run with baseline JAR/report, candidate JAR/report and an output report path.

The runtime comparison passed 32 checks. At stationary left/right steering, mean brace/deck surface gap improved from about 0.04733 blocks to its unchanged rest value of 0.004427 blocks, a 10.69× reduction. The actual headset axis and front-wheel hub stayed attached within 4×10⁻⁸ blocks. Saturated reverse phases projected full ±28.000002° using the actual wheel axle; projecting the stem forward vector would incorrectly include camber. Pose-cache time quantization allows up to 0.2164° projected yaw error.

These reports establish the tested asset and submitted-palette behavior. The isolated first-person seam screenshots are a separate visual acceptance gate. They do not establish behavior on every third-party render backend or shader.
