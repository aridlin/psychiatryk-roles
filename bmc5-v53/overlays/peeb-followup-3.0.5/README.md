# Peeb follow-up overlay

These four recovered and edited Java sources compile only the Peeb classes
changed after the historical e0f6 disc checkpoint: common grapple validation,
client controls/targeting, binding reservation and grounded rendering. They
preserve the existing packet shapes and unchanged model assets.

The common implementation retains an attached surface after view rotation or
occlusion while still checking support, range and mode. Hostile living targets
use a server-verified moving anchor and one 4-damage contact hit per hook with a
20-tick player cooldown. A controlling Kukirin rider pulls the root scooter on
the server. Client input keeps Left Alt assigned and reads its physical state
while Peeb owns the camera. Grounded rendering uses feet placement and walk/idle;
the existing winch can still lift toward a high anchor.

```sh
python build.py --base /path/e0f6.jar \
  --classpath-file /path/neoforge-classpath.txt --output-dir /path/output
```

Java 21 and the external Minecraft 1.21.1 / NeoForge 21.1.250 classpath are
required. The base SHA and four source fingerprints are enforced. The generated
follow-up must match frozen SHA
`2f83776f209b598bc3b684b8c46c0d40fc3400002bcf26f253e50395ae493fc9`.
No original addon generator, game, network or Git operation is invoked.

`source-provenance.json` identifies these edited sources and their lineage.
`native-tests/` contains the authored dedicated observer source. Sanitized
results in `../../patch-evidence/peeb-followup-3.0.5-dedicated.json` bind the
dedicated-tested EA181 artifact to the 106f checkpoint by common-entry identity.
They do not claim actual network player negotiation, physical human key input,
human grapple feel, shaderpack rendering or production deployment.
