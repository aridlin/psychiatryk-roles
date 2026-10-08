# Focused native observer

These are the actual helper sources used for the 195-assertion f03a1a28 native run.
Build with `python3 build.py --candidate /path/to/candidate.jar --sha256 f03a1a2856e3ce9d62de6797ce2fcf49825373252d733e6f69d9f5f60b57fa53 --classpath-file /path/to/classpath.txt`.

Install only in a disposable full Better MC5v53 MC1.21.1/NeoForge21.1.250 fixture
with the exact candidate. Supply system properties `peebqa.output`,
`peebqa.sha256`, and `peebqa.helperSha256`; quick-play a disposable creative
world with cheats. The helper replaces fixture blocks/inventory, creates a
remote Peeb, simulates input, captures paired frames, writes a runtime receipt
and then stops the client. Never use it in a player's game or production.

The completed receipt is in ../../../patch-evidence/peeb-rope-dither-3.0.5-native.json.
Its software-OpenGL run used shaders off; shaderpack-enabled rendering and human
grapple feel are not claimed. The fade-off frame is a QA-only comparison, not
a production feature. Raw logs, screenshots, fixture world, native libraries
and any cached profiles are excluded. No automatic launch script is exported.
