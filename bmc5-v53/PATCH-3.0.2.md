# Roles 3.0.2 restoration

Restores the original detailed scooter's geometry, UVs, vertex normals and texture variants in the merged BMC addon. Four native Minecraft GPU buffers cache the geometry per resource reload. No Create, Flywheel or GemRender dependency is introduced. Physics, storage, ownership and the six survival recipes retain the existing implementation.

Carry On excludes `goplanska_kukirin:scooter` via entity tags and the scoped configuration blacklist. Shift+right-click uses the scooter's own item pickup, retaining cargo and upgrades.

Unenchanted Vein Mining uses crouch activation with a 50-block base limit; hunger/durability costs remain enabled. All 15 production files are paired through the verified startup transaction; the private music catalog remains server-only.

The authored renderer/shader/tag/config sources are included here. The user-supplied Sketchfab mesh and texture atlases use Sketchfab Standard licensing (kovsh, https://sketchfab.com/3d-models/fddbc46d599240bba8258e6d2c4daa59). They are embedded in the finished addon as part of its implementation, not distributed here as standalone reusable raw assets. Supply appropriately licensed model inputs when rebuilding the restoration. The separate portable module retains its explicitly documented primitive model.
