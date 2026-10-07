# Roles 3.0.3 activation

Prepared locally only. Runtime qualification, SFTP queueing, restart, main Prism updates, Git push and web publication are separate operator actions.

## Payload and wrapper

The patch contains `manifest.properties` and exactly these six payloads:

```
mods/psychiatryk_roles-3.0.0-bmc5.jar
automodpack/host-modpack/main/mods/psychiatryk_roles-3.0.0-bmc5.jar
goplanska-release.json
automodpack/host-modpack/main/goplanska-release.json
config/veinmining-server.toml
automodpack/host-modpack/main/config/veinmining-server.toml
```

Each server/feed pair is byte identical. The internal addon version is `3.0.3-bmc5`; the managed filename stays unchanged. Baseline is the successful Roles 3.0.2 activation, addon SHA256 `ea63b4c4fa3287861d2066c3b636d80772d162a01e6a643e92ec55a526329ad6`. The only configuration change is VeinMining `blocks = "CONFIG_LIST"` to `blocks = "ORES"`; its paired baseline SHA is `fa7c222f073aee8b72c6f090f370e0adad05f50d8e6f10219207a0ff6fe7a1c4`, new SHA `9f0fc2fdc453db935231f876961e467dab639a6aeeee09fd80c032ac6a5cd14a`. It retains the no-enchantment cap of 50, crouching, costs and native connected traversal. All other configuration, FallingTree, private music catalog, dependencies and original scooter assets/recipes are retained. Native API/byte evidence is in `../../veinminer-ore-fix/`.

The old wrapper enforces fifteen paths and emits a 3.0.2 receipt. This new wrapper limits the transaction to six paths, emits 3.0.3, preserves the existing atomic stage/journal/commit/recovery mechanism and starts the same NeoForge 21.1.250 server. It does not rerun the completed migration. Tested wrapper SHA256: `c0bad37946474bcb88a06b02b2039b296376d50836fa73ebb98804ac9ce38431`.

## Bounded sequence

1. Root writes `../build/qualification.json` after the exact candidate passes real client/server QA. Required fields are `success: true`, `runtime_verified: true`, `candidate_sha256`, `base_sha256` and nonempty `evidence: [{"path": "absolute receipt path", "sha256": "receipt fingerprint"}]`. Each evidence receipt must have `success: true` and the same candidate SHA. The build proof, current candidate bytes, original model/licenses/recipes and qualification must agree. The candidate hash is derived from these files; it is not hardcoded.
2. Prepare a fresh local directory with `queue_3_0_3.py`. Freeze its source and verification/transport helpers after preparation. Tests bind those helper hashes to the exact wrapper. Default preparation does not connect to SFTP.
3. Root explicitly queues with the existing native SFTP adapter. Credentials stay ephemeral. Preflight checks six live baseline payloads, absent `world/serverconfig/veinmining-server.toml` (the native Spectrelib override would otherwise take precedence), the current previous successful journal/stage/receipts and prior ZIP/wrapper. It snapshots only that previous transaction, uploads and reads back the three queue artifacts, preserves the six current prior roots, installs ZIP/wrapper, verifies unchanged payloads and publishes the activation flag last. Existing historical backup roots are not scanned or rewritten. Any queue failure withdraws the candidate flag first and restores the protected previous state.
4. Root approves activation and schedules/restarts the server externally. `notBefore` is an earliest UTC activation time, not a scheduler: an early startup retains 3.0.2, and a startup at/after the gate applies the already qualified candidate. The queue helper never restarts the server.
5. `verify_live_3_0_3.py` performs read-only, bounded native readback: exact six payloads, new applied JSON/properties, absent pending flag and world override, matching six-file rollback journal, newly preserved prior artifacts, a fresh `Done` after the exact applied timestamp, and the generated AutoModpack feed. The feed must retain 1127 paths / 353 mods, identical headers and 1124 unchanged records; only the addon, release metadata and VeinMining server config rows may change. JEI/DFR records therefore remain unchanged. This receipt is the gate for client/public alignment.
6. Root's `update_prism.py` updates the closed selected main instance using the verified live readback. It touches the managed addon, selected feed, both local release/config copies and related client metadata (seven files), with a local rollback. It never launches the game. Root's `prepare_public.py` / `publish_public.py` own the matching addon ZIP including the single native config patch, new INSTALL/PLAY pages and site update; the existing bootstrap installers remain unchanged. Keep the six recipe cards, authored source/Git release and served addon bytes aligned to the same candidate; do not export private catalog/audio/accounts/worlds or raw licensed model assets.

## Commands

Run from the workspace root. Choose fresh `prepared-3.0.3-*` and `activation-3.0.3-*` directories per attempt. Replace the UTC gate and queue receipt path with the actual values.

```sh
python work/bmc5-migration/wearable-jukebox/deployment/test_deployment.py

python work/bmc5-migration/wearable-jukebox/deployment/queue_3_0_3.py \
  --proof work/bmc5-migration/wearable-jukebox/build/qualification.json \
  --out work/bmc5-migration/wearable-jukebox/deployment/prepared-3.0.3-ATTEMPT \
  --not-before YYYY-MM-DDTHH:MM:SSZ
```

Explicit root/operator queue action after reviewing the prepared report:

```sh
python work/bmc5-migration/wearable-jukebox/deployment/queue_3_0_3.py \
  --out work/bmc5-migration/wearable-jukebox/deployment/prepared-3.0.3-ATTEMPT \
  --queue
```

Root may instead reuse its ephemeral `NativeSFTP` instance with `queue(transport, prepared_dir, unique_attempt_id)`; no credential file or password literal is needed.

After root's actual activation/restart, using the production logger's actual offset:

```sh
python work/bmc5-migration/wearable-jukebox/deployment/verify_live_3_0_3.py \
  --prepared-dir work/bmc5-migration/wearable-jukebox/deployment/prepared-3.0.3-ATTEMPT \
  --queue-report work/bmc5-migration/wearable-jukebox/deployment/prepared-3.0.3-ATTEMPT/queue-ACTUAL-ID/queue-report.json \
  --log-utc-offset +0000 \
  --out work/bmc5-migration/wearable-jukebox/deployment/activation-3.0.3-ATTEMPT
```

## Local evidence

`mock-test-report.json` records ten targeted offline checks: six-file application and idempotence, UTC gate, baseline failure, seventh-path rejection, config pair rejection, interrupted recovery/quarantined false receipt, flag-last queueing, failure after flag rename, world override rejection and exact three-row feed validation. It records no game launch or network use. Synthetic fixture checks do not substitute for root's real wearable runtime qualification; the separate ore config receipt verifies the installed native enum and exact one-key bytes without an additional game launch.
