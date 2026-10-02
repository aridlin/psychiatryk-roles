# Archived Overworld access

The `psychiatryk_roles:old_overworld` dimension is separate from the new S23 Overworld. Its definition is bundled in this mod. The mod does **not** copy world files at startup or import player inventories.

With the server stopped, copy only the old Overworld's `region/`, `entities/`, and `poi/` directories to:

```text
world/dimensions/psychiatryk_roles/old_overworld/
```

Do not copy the old `level.dat`, `playerdata/`, `data/`, `DIM-1/`, or `DIM1/` into the new world. Retain the original archive separately. For the verified 1.20.1 source whose `level.dat` has DataVersion 3465 and spawn `(988, 67, 567)`, the final import marker is:

```json
{"version":1,"sourceDataVersion":3465,"spawn":{"x":988,"y":67,"z":567}}
```

Write that file as `world/dimensions/psychiatryk_roles/old_overworld/psychiatryk-import-ready.json` **only after** all copied files have been verified, using a temporary file and atomic rename. The marker prevents `/oldworld` from entering an incomplete archive. The command also checks the Anvil header for each requested chunk, refuses absent or truncated chunks, and searches for a safe landing within the same imported chunk. It does not deliberately probe adjacent unimported chunks.

Operator commands, issued while in the **new Overworld**:

```text
/oldworld                    # enter at the old spawn; toggle back while there
/oldworld enter              # enter at the old spawn
/oldworld enter <x> <y> <z>  # visit a specific archived chunk
/oldworld return             # return to the saved new-world location
```

Return locations are stored in the new Overworld's `psychiatryk_old_world_travel.dat` and survive a restart. If an exact return location is obstructed, the command searches nearby or uses the new-world spawn. The archive is exempt from the season border; the new Overworld retains its shrinking border. Entering from other dimensions is refused so the Nether/End season lock cannot strand an admin on return.

Minecraft upgrades the old 1.20.1 chunks when they are loaded under 1.21.1. An installed mod must provide the same block and biome IDs for its old content to survive; removed or renamed mods may leave missing blocks or block entities. Verify representative structures and chests in a local copy before treating the import as complete. Generated terrain beyond the archived region is not part of the old world and this command will refuse to take an admin there.

The dimension's generator produces only air in `minecraft:the_void` for any missing chunks. Existing copied chunks continue to load from their Anvil files. This avoids creating a false S23-style landscape when an admin walks past the archive edge, but it does not restore missing chunks from the original source.
