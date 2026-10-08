# Native Minecraft music catalog

This overlay adds every music/record Ogg referenced by the Minecraft 1.21.1
asset index: 60 soundtrack tracks and 19 discs, including the exact Aria Math
track. Song labels use the existing music-menu filename protocol. The catalog
also participates in existing favorites, next-track and playlist selection.

`VanillaMusicCatalog` supplies immutable Song metadata and an exact allowed URL
lookup. URLs use `minecraftsound:goplanska_kukirin:vanilla_<native-file-path>`.
The native client playback branch must resolve only a URL returned by
`VanillaMusicCatalog.forUrl`, then create its sound event from the URL suffix.
The expected existing Song getter methods provide durationTicks and title.

Generated sound definitions reference `minecraft:<native-file-path>` with
`stream: true`. Minecraft resolves them from its own installed assets and active
resource packs. No native audio, disc texture or Minecraft binary is copied
into this overlay or a public archive. Existing scooter sound definitions are
preserved.

`prepare_catalog.py` verifies the official asset-index SHA-1 pinned in the
installed Minecraft 1.21.1 metadata, the sound-definition object and each native
audio object's SHA-1/size. It reads duration with ffprobe and produces only song
metadata and own sound aliases. It writes an isolated ScooterMusic source copy
for catalog selection; it does not modify production, a running Minecraft
instance or the reconstructed source baseline.

The helper and patched server music class compile against the existing 1.21.1
classpath. `VanillaMusicQA` checks full catalog coverage, exact Aria Math
resolution, measured duration, name/URL consistency and rejection of unlisted
or traversal URLs. Native listening is not claimed by those checks; the
coordinated music overlay supplies the actual client playback branch.

## Portable overlay build

```sh
python build.py --base /path/focused-addon.jar \
  --classpath-file /path/neoforge-classpath.txt --output-dir /path/output
```

Java 21 and the external Minecraft 1.21.1 / NeoForge 21.1.250 classpath are
required. Source/resource hashes and generated class hashes must match the
frozen provenance before the metadata-only overlay is written. No game,
network or Git operation runs.

The catalog is already frozen here. Regeneration is optional and requires an
external installed Minecraft asset tree, its version metadata, ffprobe and
`--music-source /path/historical/ScooterMusic.java` for the three catalog edits.
The generator reads and checks native audio locally; it exports only metadata
and source, never audio. Its 247 catalog checks cover all 79 references.
