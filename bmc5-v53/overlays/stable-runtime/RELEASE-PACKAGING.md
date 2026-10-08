# 3.0.10 local release packaging

`python package_release.py` reads the queued 3.0.9 patch, the frozen 3.0.10
roles JAR, and pinned KubeJS/Rhino JARs. Each input is SHA-256 checked. The
generated files are under ignored `build/release-3.0.10/`:

- `psychiatryk-3.0.10-payload.zip` with paired server and AutoModpack hosted
  JARs, paired release metadata, and per-file `manifest.properties`.
- `release-manifest.json` with archive and member hashes.
- `INSTALL-AND-ROLLBACK.md` with the baseline check, backup requirements,
  stopped-server replacement boundary, and rollback steps.

This only prepares bytes locally; it does not qualify or deploy them. In
particular, the live `PatchActivation` bootstrap embedded in
`bootstrap-live.jar` accepts the four old roles/metadata paths, so it cannot
apply the eight-file bundle. Never rename the bundle to `bmc5-patch.zip` or
publish an activation flag. A stopped-server transaction must preserve the
current world, live release, hosted feed, runtime signing key and config;
client registry synchronization requires the one-time matching 3.0.10 update.
The runtime qualification and actual join gates are in `QUALIFICATION.md`.
