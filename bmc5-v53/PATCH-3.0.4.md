# Placed jukebox acoustics — Better MC5 addon 3.0.4

Custom server songs from a placed jukebox now refresh Sound Physics muffling
and reverb as the listener moves. The 3.0.3 hook incorrectly required an entity
source, so it skipped block-backed music even though initial processing ran.

The fix removes that one source-type condition. The hook still processes only
our music class, checks that it is active and audible, uses the sound executor,
staggering every ten ticks and the existing four-source budget. Global moving
sound processing stays disabled; unrelated mod sounds are not added to this loop.

`build.py` pins the qualified 3.0.3 addon and changes one client mixin plus five
internal version markers. All common/server/network classes, other client
classes, assets, recipes and dependencies retain the exact original bytes.

The focused native regression uses the actual placed jukebox menu/network and
OpenAL/Sound Physics environment setter. It moves the listener behind a stone
wall and back while the block source remains stationary. Qualification records
its completed scope and any fixture limitations; it does not claim a human
listening result or a new dedicated-server startup.

The native AutoModpack host can regenerate this compatible client release while
the server continues running 3.0.3. Paired disk JAR/release files are renamed
through a reversible four-path transaction, with staging outside the client
feed. After regeneration, the full feed must retain every existing record except
the addon and release metadata. The next normal server restart loads 3.0.4.

Clients download 3.0.4 at their next launch. A separate, temporary live JVM fix
can apply the equivalent predicate change to the current game; it is not part of
the addon or public source export. No private catalog or audio is redistributed.
