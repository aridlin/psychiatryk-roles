# Incremental music playback

Remote tracks stream directly from the existing authenticated ProLiant WAV URL.
The client opens and validates the WAV header, then supplies bounded PCM chunks
to Minecraft's native AudioStream. Playback starts before the complete response
arrives. No complete remote audio file or decoded PCM array is held on the
client. Natural completion checks the full content SHA; TLS and the authenticated
content-addressed server protect the stream before that final hash is available.

The parser supports PCM16 RIFF and RF64, reads at most32KiB per network operation
and produces at most1MiB for a native sound buffer request. Looping reopens the
same authenticated URL; Stop, source removal and dimension changes close the
connection. Remote/native source counts have no four-source rejection. Two
header I/O workers queue requests rather than reject them. The previous four
source memory budget applies only to legacy complete-PCM playback.

Native Minecraft tracks use exact whitelisted `minecraftsound:` aliases from
VanillaMusicCatalog and the player's installed sound assets. Audio is neither
copied into the addon nor exported with source. The native library overlay is
supplied separately to the build.

The service retains complete imported media on ProLiant and serves it
incrementally. Its former10-minute,16MiB WAV,64MiB input, worker wall-clock,
configured256-track and2GiB-library limits are removed. Provider host checks,
public-URL rules, authorization, finite positive duration validation, bounded
headers/artwork and connection inactivity timeouts remain. Playlist import
requests use zero to mean all entries. Administrative cache/track CLI options
default to zero (unlimited).

Existing packet fields are signed32-bit integers. Public `bytes` metadata
saturates at2,147,483,647 for compatibility; validation uses the actual full
file size and hash, and incremental playback uses RIFF/RF64's long data length.
An18-hour22050Hz mono PCM track therefore remains playable past2GiB. Tick
metadata saturates only at the protocol's natural integer boundary, roughly
3.4years, and does not control the client's stream termination. The former24-hour
HUD duration clamp is removed.

## Build

Supply the exact frozen common-side addon, a Minecraft1.21.1/NeoForge compile
classpath file, and the authored native-library and Loyalty overlay archives:

```sh
python build.py --base /path/base.jar --base-sha256 EXACT_SHA256 \
  --classpath-file /path/neoforge-classpath.txt \
  --vanilla-overlay /path/vanilla-music-overlay.zip \
  --loyalty-overlay /path/elytra-loyalty-overlay.zip \
  --output-dir /path/output
```

This compiles only the authored music source, applies the exact overlays and
preserves all other base entries. It writes an immutable candidate snapshot,
per-entry overlay fingerprints and composition proof. It invokes no original
addon generator, network, game, account, production connection or Git operation.

The Java loopback fixture passed68 checks for early PCM, bounded reads, RIFF/RF64,
looping, cancellation, rejected redirects and final content hash. The offline
service fixture passed11 checks with a66MiB input and a1569-second complete track,
and accepted10-hour metadata. Those checks do not claim real provider extraction
or native game playback. Separate focused client and dedicated server receipts
must bind the final candidate.

## API operator package

`build/proliant-service.zip` contains only the reviewed service source, manifest
and `operator_activate.py`. On ProLiant, run the extracted operator as root with
`--stage /path/to/extracted/package` for read-only validation. The explicit
`--activate` switches only `goplanska-ytmusic.service`, after checking the reviewed
`caf33999a66e1fcb22a7` baseline and zero existing imports. It installs an immutable
release, reuses the installed extractor vendor directory, preserves the original
unit and data, and records a recoverable drop-in under the established backups
directory. It never reads or copies credentials and never controls Minecraft.
