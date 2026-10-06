# S23 v30

Bound scooter dots and halftone share the scooter paint palette rather than the owner's team color. Loaded jeb_ scooters use their current rainbow color.

Server index cleanup removes entries when dropped items are picked up or discarded. Chunk unload keeps the saved position and paint for distant markers and recall. Legacy ghost records are removed only in fully loaded entity chunks after a two-second deserialization grace. Marker repair never force-loads distant chunks.

31 real dedicated-server checks passed on Java 21: actual pickup/discard, unload preservation, legacy pruning, server marker payload, all 16 dyes, saved-data migration and animated rainbow color. The candidate JAR matches the tested one exactly. No Minecraft client was launched or restarted for these tests. The previous v29 headlight/audio/wall-kick fixes are preserved.

Private credentials, logs and user music are excluded. Component licensing is preserved.
