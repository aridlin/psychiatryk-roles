# Sable save IO candidate

Designed for the exact Sable 2.0.5 API shipped in Goplanska v31. No original mod JAR or production files changed.

## Integration

Compile `src/**/*.java` with Java21 and the current NeoForge/Sable classpath. Add classes plus `resources/psychiatryk-save-io.mixins.json` to the unified roles JAR and register the mixin resource in mods metadata.

Root must add successful-save completion hooks:

- Explicit flush=true `ServerLevel.save` / `/save-all flush`: call `SableIoBarrier.flushAll()` before acknowledging completion.
- Server stop: call `flushAll()` before stopping; map/storage close itself also drains and closes on the same worker.
- IOException must surface as failed completion, not be reduced to a successful command message.

Public API `pl.aridlin.psychiatrykroles.io.SableIoBarrier`:

- `flushAll() throws IOException`: real force(true), after preceding writes, for every open Sable storage owner.
- `pendingJobs()`, `activeOwners()`, `failedOwners()`.

No DSYNC or fsync removal. Worker receives deep copies of sublevel tags and holding tags serialized on tick thread. All cache lookup/eviction/allocator/file accesses are owned by one serial worker per storage. New allocation and reads wait intentionally. Ordinary autosave flush and cache prune queue without waiting. Storage close waits, then unregisters its owner.

A failed asynchronous save remains first; subsequent asynchronous jobs cannot overtake it. Retry occurs every two seconds, with errors logged no more than once a minute. New synchronous calls are rejected before enqueue while this failed prefix exists. Unstarted synchronous calls already queued behind a newly failing save are removed and completed exceptionally. A started synchronous operation's own failure is surfaced once and never retried secretly. Failed owner reads/allocations throw UncheckedIOException instead of masquerading as absent data.

Queue backpressure is enforced before accepting a job at 1024 pending jobs. A failed full queue throws before append, preserving its existing retained snapshots without unbounded growth. This state should be monitored and treated as an actual storage fault.

`classes/` contains a successful Java21 compilation. `OwnerFailureQA.java` and `OwnerBoundQA.java` pass on Java21, verifying allocation cancellation, no hidden synchronous retry, retained asynchronous FIFO recovery, and a bounded faulted queue. These are owner-level tests, not a verified transformed-server runtime. `classpath.txt` is local only. Actual mixin transform, repeated slow-disk autosave, explicit flushed save and reopen/reload tests remain required before deployment.
