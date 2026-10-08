# Spinning jukebox disc

- Removed the album-art prerequisite that hid the entire disc for legacy songs, missing covers, and covers still loading.
- Added a recognizable vinyl fallback with rotating asymmetric markings at 33⅓ RPM.
- Preserved masked rotating album art when present and provided a worn-jukebox fallback panel.
- Separated the face from the block surface and gave the vinyl a thin physical rim.
- Submitted texture materials sequentially so switching a dynamic buffer cannot invalidate the preceding vinyl writes.
- Kept Peeb and all other checkpoint entries unchanged. The build and geometry evidence are local implementation checks; native visual evidence belongs to the coordinated release gate.
