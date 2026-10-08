# Recovered music API source

These Python files were recovered, read-only, from the active user-owned
ProLiant `goplanska-ytmusic` release `caf33999a66e1fcb22a7` on 2026-10-08.
The known public API base is `https://prol.aridlin.pl/scooter-music-api/v1`.
See `source-report.json` for exact remote/export hashes and import inventory.

The three exported files are byte-identical to that release. They passed syntax
and embedded-credential checks without execution. Environment files, credentials,
private config, data, cookies, caches, media, logs and virtual environments are
excluded. No service was changed and no publication was performed.

## License

No LICENSE/COPYING file was present in the bounded live-release source inventory.
No license or third-party redistribution right is inferred by this recovery.
The recovery adds no license and performs no relicensing. No SPDX or license header was found in the three recovered source files.
This recovery does not establish or change their license. Runtime dependencies
are not included and retain their respective licenses.

## Runtime dependencies and configuration

`requirements.txt` is derived from the reviewed `yt_dlp` import; it was not
recovered from the service and does not pin the uninspected installed version.
Other Python imports are standard-library or the included common module. The
worker also requires FFmpeg on PATH and Node.js at `/usr/bin/node` for extraction.
No dependencies are vendored.

The service reads its bearer token from a separate credential JSON supplied with
`--credential`; that file is deliberately not exported. It also takes `--state`
and optional port/cache/track limits. The recovered defaults include the original
public API URL and optional legacy-audio path. Adapt those explicit source
constants to another deployment. This source export is not an install unit or a
new security/runtime qualification. It does not copy the original service data
or change a running service.
