# Local 3.0.12 HUD and signal candidate

This source-only overlay builds a matching client/server JAR from the exact QA-passed 3.0.11 music hotfix JAR (`4a5fdc0a58b0d453509260c628970c974ad341c08fca9f359c5e27ccc56665d2`). It compiles only the reviewed HUD and signal sources using Java 21 with a 384 MiB compiler heap. The builder permits 16 existing class paths to be replaced, exactly 12 new class paths, and the six `3.0.11-bmc5` metadata version fields to become `3.0.12-bmc5`. Every other input archive entry, including the four music fix classes, must remain byte-identical.

Run from the repository root:

```sh
python bmc5-v53/overlays/combined-signals-3.0.12/build.py \
  --base /absolute/path/to/psychiatryk_roles-3.0.11-bmc5.jar \
  --legacy /absolute/path/to/psychiatryk_roles-3.0.10-bmc5.jar \
  --sdk /absolute/path/to/sdk-clean.txt
```

`--legacy` must be the canonical 3.0.10 JAR with SHA-256 `eb4b1685205ba1ac3432b1708572d829e9004193ca8857b8b05798bc48f6ce12`. The output is ignored `build/psychiatryk_roles-3.0.12-bmc5.jar` plus `build-proof.json` and `compile.log`. The builder runs eight signal/HUD test mains against the packaged candidate with 256 MiB runtime heaps and an unchanged 3.0.10 HUD parser test against the legacy JAR. It verifies the entire archive roundtrip and byte preservation after writing the candidate. The current local candidate hash is `6fbde292e6e88a0fa250966203c173ca06ebf77b36d1530eab4de82763f12566`.

The same JAR bytes are intended for server and client, but this builder does not make installer archives, edit the live AutoModpack feed, or deploy. It has not run an exact-pack dedicated server or a game client. See `../stable-runtime/SIGNAL_PROTOCOL.md` for the supported API, limits, handshake, and old-client HUD fallback. This source layer exposes signals to HUD expressions; Peeb and scooter visual expressions and runtime menus have no signal bindings yet.
