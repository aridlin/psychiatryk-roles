# Main KuKirin restoration rebuild

This is the main Better MC addon restoration. The separate portable scooter project deliberately keeps its original primitive model.

## Build from the pinned workspace

From `<workspace>`:

```sh
python3 work/bmc5-migration/kukirin-regression-fix/build.py
```

The script rejects any base JAR other than `work/bmc5-migration/next-patch/psychiatryk_roles-3.0.1-bmc5.jar` with SHA256 `f510fa2dd1b5bc111277315b5323bbff21d95f2f4f9e60618b7a2892c9e43239`. It compiles the two restored main renderer source files against the cached Minecraft/NeoForge libraries, then overlays their classes and restored assets into a separate 3.0.2 candidate.

Expected output: `work/bmc5-migration/kukirin-regression-fix/build/psychiatryk_roles-3.0.2-kukirin-restoration-candidate.jar`, SHA256 `ea63b4c4fa3287861d2066c3b636d80772d162a01e6a643e92ec55a526329ad6`. Read `build/build-proof.json`; its success flag, base fingerprint and candidate fingerprint must match. A changed fingerprint needs a new qualification before release.

The original build generator, `work/bmc5_build_addon.py`, imports the portable scooter module and therefore reintroduces the primitive main appearance if used alone. Its output is an input candidate only. Apply this restoration overlay and pass the qualification gate before making any future main release. Do not publish an unqualified generator result as the main addon.

## Required original assets and license

The build uses the preserved `work/kukirin/resources` model and atlas tree: repaired `kukirin_g2.glb`, original 9,126 triangle `kukirin_g2.mesh`, `scooter-rig.json`, 17 dye/default atlases, three rental atlases and `licenses/sketchfab-scooter.txt`. GLB UVs/normals are checked against the mesh vertex order before generating `kukirin_g2.uvnorm`. These inputs must be available; substituting the portable primitive does not satisfy this restoration.

The user supplied model is by **kovsh**, from [Sketchfab](https://sketchfab.com/3d-models/fddbc46d599240bba8258e6d2c4daa59), under the **Sketchfab Standard** license. Keep both bundled license notices. The Java GPL notice does not relicense the model or derived atlases. Do not add these licensed assets to the separate portable primitive project. This workspace-bound overlay script is not a clean standalone source build; its base JAR, cached libraries and original licensed asset inputs are explicit dependencies.

## Release gate

Use `build/qualification.json` or the deployment copy `deployment/qualification-report.json` as the concrete gate. Require `success` and `runtime_verified`, the pinned base SHA, the exact candidate SHA, and matching SHA256 references for `model_visual_proof`, `pickup_runtime_proof` and `mining_runtime_proof`.

The final model proof explicitly inherits the successful eight-scene 08b0 visual run because every existing class/resource is byte-identical; the only final delta is two effective singular `entity_type` CarryOn tags. The final candidate itself passed native connected mining and pickup checks. Evidence includes GPU reuse/reload, opposite steering/trim, original dye/rental textures, rental visibility packet, lighting, mounted vehicle/grip geometry, one-item pickup with complete components/cargo/NBT, and ordinary CarryOn chicken pickup. It does not claim a new eight-scene run for the final tag-only JAR, shader-pack compatibility, a human FPS benchmark or visible first-person arms.

The overlay preserves all existing recipes, item JSON, physics, storage, grip/rider pose and pickup bytecode. Runtime behavior added by the renderer is only the restored rendered-rental visibility callback. CarryOn exclusions target `goplanska_kukirin:scooter` specifically; both singular effective and plural compatibility tags are included. Root deployment separately applies the recorded CarryOn and VeinMining configuration files.
