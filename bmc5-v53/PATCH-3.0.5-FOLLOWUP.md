# Roles 3.0.5 follow-up source checkpoint

This additive source export describes final candidate SHA-256
`53c622c5b69e1d264eacee2e9b9d686e663d3e08d28a905523dd85fa07f5d509`.
Production activated this exact artifact on 8 October 2026 at 09:53 UTC.
The server, native AutoModpack downloads and public website files match.
The sanitized operator receipt is [production-3.0.5.json](patch-evidence/production-3.0.5.json).
Tests of this artifact do not qualify later artifacts.

## Changes

- Peeb uses the physical Left Alt key while Peeb owns the camera, reserves
  competing Alt mappings during that mode, and retains the assigned binding.
- Attached ropes survive turning away and later occlusion. Initial attachment
  still requires reach and line of sight; removed support, excessive distance,
  mode exit and invalid targets release the rope.
- Server-validated hostile living targets provide a moving anchor and one
  wooden-sword-strength contact hit (4 damage) per hook, with a 20-tick player
  cooldown across reattachment. Allied targets and prohibited PvP are rejected.
- A controlling Kukirin rider can grapple. Pull acts on the root scooter on the
  server, preserves existing momentum within vehicle limits, and synchronizes
  the vehicle motion. The client does not apply a second rider pull.
- Grounded Peeb renders with feet positioned for the grounded pose and walk/idle
  animation. A high anchor can still lift the player through existing physics.
- Authenticated remote WAV playback starts incrementally, supports PCM16 RIFF
  and RF64, loops at actual EOF and closes its connection on Stop. Remote and
  native sources have no former four-source rejection.
- The native library includes 79 Minecraft 1.21.1 references: 60 soundtrack
  tracks and 19 discs. The player's installed Minecraft assets supply the audio.
- The existing Elytra/dragon-egg-shard scooter recipe adds default Loyalty I.
  Its grid, count and prebound marker are unchanged.

- The 23 original scooter modification recipes are restored and admitted by
  exact ID and recipe type. Dye, Nether Star and other upgrades again use the
  smithing table; the dismantler and unlisted custom creations remain blocked.
- Held Peeb works in either hand with full armor. Wearing it retains the empty
  helmet, leggings and boots requirement; two armor tooltips clarify that scope.

## Source and build lineage

The historical `peeb-music-3.0.5` and `jukebox-disc-3.0.5` trees remain the source
checkpoints for the earlier f03a and e0f6 builds. This export adds sibling trees
and supplemental documentation; it does not replace their historical claims.

| Checkpoint | SHA-256 |
| --- | --- |
| Historical disc base | `e0f6a9df96d249ff7a6d9477ddcc04bc90e5b446c3210f3cf47e8d887be7e66d` |
| Four-source Peeb follow-up | `2f83776f209b598bc3b684b8c46c0d40fc3400002bcf26f253e50395ae493fc9` |
| Dedicated tested composition | `ea181b1ae5302fa48a83d79030672b1bb1a9bfe11fcb77edb51860440c979e42` |
| Source checkpoint composition | `106f9337eb3f567e61e48a762c58e198331ccf95ca36a94e00e8695873469d85` |
| Final upgrade/held-armor restoration | `53c622c5b69e1d264eacee2e9b9d686e663d3e08d28a905523dd85fa07f5d509` |

Use Java 21 and supply the historical e0f6 JAR plus an external Minecraft
1.21.1 / NeoForge 21.1.250 classpath. No game, network or Git command runs:

```sh
python build-followup-3.0.5.py --base /path/e0f6.jar \
  --classpath-file /path/neoforge-classpath.txt --output-dir /path/build-output
```

The builder verifies input/source hashes and the resulting exact checkpoint
SHA. This tree includes authored/recovered edited Java source, Python builders,
service source, metadata, recipe JSON, tests and sanitized receipts. It contains
no runtime JAR, compiled class, audio, model data, credential, private log or world.

## What was tested

- The isolated dedicated server loaded the EA181 checkpoint with Minecraft
  1.21.1 / NeoForge 21.1.250: 295 mod JARs and 399 loaded mods. All 62 observer
  checks and 11 nested Loyalty checks passed. Tests include actual registered
  Peeb codecs, actual recipe assembly, retained/released anchors, moving enemy
  synchronization, contact damage/cooldown and mounted scooter motion.
- The dedicated fixture retained JEI, selected Sophisticated Backpacks/Core and
  the paired Carry On/ore-only VeinMining configuration fingerprints. The
  Distraction Free Recipes artifact remained retained for clients; it is a
  client-only mod and was not installed as a dedicated-server mod.
- Minecraft status responded, all dimensions saved, and the normal server stop
  completed. A baseline Sawmill/Moonlight executor remained alive after stop;
  bounded SIGTERM cleanup produced JVM exit 143. This is not a clean JVM exit.
- EA181 and 106f differ only in `ScooterAudioClient.class` and
  `ScooterHttpAudioStream.class`. The other 683 entries, including all common
  server classes, recipes and metadata, are identical. The dedicated results
  bind to 106f by that recorded identity; 106f did not receive another full
  dedicated launch.
- The streaming fixture passed 68 checks; the offline import service fixture
  passed 11 checks, including a complete 66 MiB input and 1569-second track.
  The native metadata fixture passed 247 checks covering all 79 references.
- The exact final 53c622 artifact received a fresh full dedicated launch. All
  63 common observer checks, 11 Loyalty checks and 243 focused upgrade/armor
  assertions passed. All 23 recipes are loaded, discoverable through native
  smithing lookup and assembled with component preservation. Held main/offhand
  Peeb works with full armor; worn Peeb and equip retain their restriction.
  Status responded, all dimensions saved and the JVM exited cleanly with code 0.
- The focused native-client fixture passed 40 checks on exact 106f, with nine
  actual OpenAL playing samples, native Aria Math and Cat, four authenticated
  HTTP EOF-loop requests and six simultaneous sources. Final 53c622 audio,
  client binding/renderer and catalog entries are byte-identical; its receipt
  records that inheritance rather than another full client launch. The fixture
  used OpenAL's null backend, not audible output to a player.
- Physical human Left Alt input, human evaluation of grapple feel,
  shaderpack-on rendering, actual network player negotiation, real provider
  extraction are not claimed by these QA receipts. Production activation and
  external native downloads are recorded separately in the operator receipt.

The sanitized details, fingerprints and final production receipt are in
`patch-evidence/`.
