# Clean-install duplicate audit

The official AutoModpack 4.0.6 NeoForge 1.21.1 binary, SHA-256 `e76570a113ac9cd7fecc85fc6d2323ef2e04318e4b7615d34519843adfe838f5`, was inspected locally. No AutoModpack binary was modified or redistributed.

## Actual defect

`FileInspection.getModInfoFromToml(reader, "modId")` iterates `[[mods]]` and returns only the last declared ID. For the current six-component Roles JAR it returns `psychiatryk_peeb`. `ModpackUtils.lambda$getDupeMods$3` compares only the two single `Mod.modID()` values. Its `getDupeMods` result also allows only one base-file match for each managed file.

The native nine-check fixture calls those actual 4.0.6 methods against the exact merged addon. It verifies that old separate `goplanska_kukirin` and `goplanska_party_markers` files are both missed. The two fresh public installer manifests contain only the official AutoModpack reference, no runtime JARs. Reusing an old instance can retain unmanaged legacy components outside AutoModpack's managed pack directory.

AutoModpack's existing removal path uses `CustomFileUtils.executeOrder66(path, false)` to delete/dummy the file; it is not a reversible quarantine feature. Enabling wider remote deletion is not a repair for this detection defect.

## Read-only repair prototype

`prototype/ContainedModConflicts.java` indexes every actual NeoForge `[[mods]].modId` and returns **all** local-to-managed overlaps, retaining local-only IDs. The latter prevents treating a local combined JAR with unrelated components as disposable. It never moves, deletes, rewrites or loads a mod.

Twelve focused checks include the actual six-ID addon, both legacy components, reordered declarations, existing primary-ID duplicates and an unrelated component inside a combined local JAR. This is an isolated detection prototype, not a deployed fork or an automatic cleanup claim. An upstream repair needs an all-IDs representation and one-to-many conflict handling; simply changing the equality predicate still loses multiple base files. A future automatic remediation must be a separate reversible, user-visible quarantine operation and must reject files containing unrelated components.

The authored prototype uses the TOML parser already shaded into legally obtained AutoModpack. To reproduce it, provide that official binary and its local Gson/Log4j runtime dependencies as the classpath, compile with Java 21 and run the two fixture classes with the exact merged addon path. The tests create only disposable local fixture JARs.

## Published guidance

The new installation guides and bootstrap overrides recommend a new launcher instance. For the known reused-instance error they name only `goplanska-kukirin-1.0.0.jar` and files beginning `goplanska-party-markers-2.0.`, to move outside all loaded mod folders into a backup. They explicitly retain AutoModpack and merged `psychiatryk_roles-3.0.0-bmc5.jar`. No friend-PC files were changed.

This revision changes documentation and its packaging only. The corrected addon remains SHA-256 `56cc13966c2ed202ff54ac9e0b401fce32325842d93fff2be22b1ee14de9b614`; bootstrap manifests, crop configuration, recipes, metadata, TLS identity and other runtime bytes are preserved.
