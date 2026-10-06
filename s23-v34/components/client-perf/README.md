# Client allocation candidate (2026-10-06)

Candidate mixins compiled for the existing Flashback NeoForge Fixed **1.0.14**. No active-client changes or launches.

## Why capture cannot be disabled entirely

`ModdedPayloadSnapshotCache` accumulates mod state before recording starts. The Recorder snapshot hook consumes that cache at the beginning of a recording, so disabling all idle capture would break recordings started partway through a session.

The existing cache's private `shouldExclude` already discards namespaces minecraft, neoforge, sable, flashback and flashback_neoforge_fixed, as well as transient/connection-scoped payloads. These PLAY payloads receive a full wire copy anyway, although nobody consumes that copy while `Flashback.RECORDER` is null. The candidate gates only those already-excluded idle PLAY packets, using an invoker to the exact existing policy. Every configuration packet and every snapshot-eligible mod packet keeps original capture behavior. A non-null recorder, including paused recording, keeps original capture behavior. The start-of-recording hook independently snapshots Sable sublevels through `SableCompat.collectSubLevelSnapshots`.

## Reflection

`readField` walks class hierarchies using exception-based absent-field discovery. `findEntityId` repeatedly scans record metadata and looks up two often-missing getters. The candidate caches only class metadata, including absent members, in ClassValue maps; mutable payload values are read each time. Field hierarchy behavior, record/getter precedence and invocation-failure handling follow shipped bytecode. Other snapshot-target methods remain unchanged.

## Integration

Package `classes/pl/aridlin/psychiatrykroles/clientperf/**` and register `resources/psychiatryk-client-perf.mixins.json` in the roles mod. Do not package QA classes. The mixins are client-only, @Pseudo for optional Flashback, required injections when a target exists. Java 21 classpath is recorded in `classpath.txt`.

## Verification

`ClientPerfQA.java` extracts original readField/findEntityId/shouldExclude methods from the real 1.0.14 jar into an isolated reference class using ASM. It compares cached metadata results against those shipped implementations for missing/inherited/shadowed/mutable fields, record/getter precedence, and throwing accessors. It executes the real compiled injection handler (substituting only the volatile recorder field and invoker bridge) against real ByteBuf and CallbackInfo across 32 policy cases. It checks buffer contents, reader/writer indices and reference counts remain unchanged. It measures allocation with exact per-thread ThreadMXBean counters.

This does not confirm a live Mixin bootstrap or recorded replay playback; those require the next authorized isolated client launch. The live 30-second JFR established the allocation cost, while performance improvement after installation remains unmeasured.

## Sound Physics proposal (not applied)

The current main profile has 32 environment rays × 4 bounces, 16 occlusion rays, a 5-tick moving-sound update, and a 512-block processing radius. JFR sound-engine stacks overwhelmingly show Sound Physics ray/block/fluid work. A first conservative proposal is **24 environment rays, 2 bounces, 8 occlusion rays**, keeping the existing 5-tick moving updates; keep sound direction evaluation and wall muffling enabled. This retains acoustic behavior but reduces quality/work. Do not claim exact speedup until measured, and do not change the radius without checking custom music range.
