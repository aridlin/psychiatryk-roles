# Per-client scooter highlights

`/chams scooter off` disables both the bound-scooter halftone/outline and its
top dot for the player issuing the client command. `/chams scooter on` restores
both. Other highlight categories and party waypoint dots keep their settings.
Preferences persist in `config/goplanska-chams.json`; old files without the new
`scooterHighlights` field retain the previously enabled scooter behavior.

Both `HalftoneChams.Events.render` and `PartyMarkers.project` use the same
`ChamsCategories.memberEnabled(kind)` predicate. The toggle does not change
server ownership, scooter indexing, party membership or network payloads.

`build.py` compiles only ChamsCategories, HalftoneChams and PartyMarkers against
the actual v31 unified JAR and the existing party-markers compiler dependencies.
`build-report.json` names the exact bytecode differences. `ScooterToggleQA.java`
tests legacy migration, persistent off/on, unaffected categories, and the actual
registered client command tree in a disposable working directory. It launches
neither Minecraft nor an isolated server. The local classpath.txt is private
workspace infrastructure and is excluded from public source packaging.
