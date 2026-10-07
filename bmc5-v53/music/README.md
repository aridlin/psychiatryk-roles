# Staged BMC5 music patch

This authored music overlay applies to the canonical portable scooter sources and is included in the staged combined Roles 3.0.1 candidate. Production activation is handled separately.

## User controls

- Scooter: open its inventory, choose **Music**, search the shared song list and click a track. A note-block upgrade is required for WAV playback.
- Jukebox: **Shift + right-click** opens that same searchable song list. Normal right-click remains vanilla.
- Both browser targets have **Loop** and **Stop** controls. Moving away from a jukebox prevents stale menus from controlling it; breaking it stops its custom audio.
- `/scootermusic <filename>` retains tab completion. `/scootermusic loop true|false` controls a scooter's repeat setting.

## Music hosting

The user's verified 635-track local snapshot is preserved. On ProLiant, 628 unique content-addressed WAV files contain the same tracks (seven duplicate-content tracks share files), totalling 3,393,995,346 bytes. Each remote file's SHA-256 was verified. The dedicated HTTPS endpoint requires its own bearer credential and has no directory browsing. Authentication, a complete sample download, range response and the existing homepage were checked.

The private catalog belongs only on the Minecraft **server**:

`config/goplanska-scooter/music-sources.json`

It contains `baseUrl`, `bearerToken` and an array of `songs` with `id`, `title`, `sha256`, `bytes` and `durationTicks`. The server watches it in a background thread every five seconds and atomically retains its last valid snapshot if an edit is invalid. Editing/replacing this JSON does not require a restart. Currently playing tracks keep their original source metadata; choose the track again to use an updated source.

**Exclude the entire private catalog from AutoModpack, public ZIPs, Prism copied configuration and Git.** Nearby listeners receive only the protected URL needed for their selected track; the catalog/browser packet contains song names, never credentials. Do not export the hosting-private JSON or private library manifest.

## Bounded playback

The Minecraft server holds and sends small playback metadata for remote tracks, rather than buffering WAVs and pushing them through its tick/network loop. Clients download asynchronously into `cache/goplanska-scooter/music/`, verify exact byte length, RIFF/WAVE and SHA-256, then decode mono PCM away from the render thread. Cache reuse avoids repeated downloads. The cache limit is128 MiB, each WAV is limited to16 MiB, two downloads execute concurrently and eight can wait. There are at most four active sources per server/client. Redirects are rejected, credentials are omitted from diagnostics and cache paths are content hashes. Connection/read timeouts bound stalled reads.

Scooter sources follow the entity; jukebox sources stay at the block. Both remain spatial BLOCKS sounds so the configured Sound Physics processing can apply. Looping queues the next sound tick safely. Session guards prevent delayed downloads from reviving stopped audio; changing dimensions clears old pending sessions. Bundled fallbacks continue to work without the host.

## Verification and publication boundary

- `hosting-report.json`, `remote-library-report.json`: real ProLiant checks, no credentials.
- `qa/cache-report.json`: synthetic Java HTTP/cache checks.
- `qa/MusicRuntimeQA.java`: full-BMC dedicated-server helper (synthetic players/flatworld only).
- `build-proof.json`: exact music-class hashes and test candidate.

The private server fixture, `upload/` symlinks, all audio binaries, `*-private*` files, logs, account caches and downloaded/copied pack configuration must never enter a public source archive. Export only authored `src/`, authored QA Java and scrubbed receipts. These files prepare the separate next patch; hosting success does not imply that production has activated it.

The final standalone music component also guards jukebox validity with `hasChunkAt` before looking up its block entity, so stale music/menu state cannot synchronously load distant chunks. Remembered source preferences are bounded to256 entries. The shared GUI list never exceeds1024 entries, and the catalog rejects full URLs longer than its packet bound. Packet/song diagnostic strings deliberately omit both protected URLs and credentials.

## Final genuine client gate

The final combined candidate `f6b413222daf31ed667bae71e90891407456bc21ebccbc0be1f98594d02b4a5a` passed 24 actual full-BMC client checks and 19 dedicated-server checks. The client exercised Shift + right-click on a real jukebox, the searchable 635-track library, authorized HTTPS download of the silent two-second QA WAV, exact cache SHA, active Minecraft sound-engine playback, repeated sound instances, both Stop operations, cache reuse and server command completion. This proves the actual engine path; it does not claim a human listening check.

The first clone inherited master volume 0.0, so its sound-active check failed. The corrected-volume run then exposed a real old-session loop resurrection after Stop; that failed receipt is retained in `../music-client-qa/outputs/stale-loop-fixture/`. The next helper incorrectly expected an unchanged cache timestamp; the cache intentionally updates it for LRU order. That failed receipt is preserved in `../music-client-qa/outputs/lru-fixture/`. The final assertion checks unchanged cache file identity and a monotonic LRU timestamp. The final fix explicitly stops removed sounds and rejects already-stopped or map-obsolete instances before queuing loops. The final gate also checks that Stop remains effective beyond the original track duration. That receipt is retained in `../music-client-qa/outputs/muted-fixture/`; the corrected run sets master volume to 1.0 only in the disposable clone. Both servers stopped normally. The successful final run used direct cached official Minecraft/NeoForge launch arguments and synthetic offline identity/token 0, with no copied real account credentials. The three recorded main-profile hashes remained unchanged. `runtime-report.json` records the exact compatible Backpacks/Core JAR hashes and retained JEI 19.27 hash.
