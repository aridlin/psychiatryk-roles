# Dependency and source provenance

The Java renderer and GLSL shaders were extracted from the authored Psychiatryk
party-marker/chams implementation in this workspace. Game integration was
rewritten behind a standalone public provider API. No Sketchfab model, scooter
asset, Flywheel/GemRender implementation, Immersive Portals source, StorageFinder
source, WAY source, or EntityCulling source is included.

Required external runtime: Minecraft 1.21.1 and NeoForge 21.1, Java 21. Their
licenses remain their own; Minecraft classes, NeoForge binaries and bundled
runtime libraries are not redistributed in the source archive or mod JAR.
Gson, JOML, LWJGL and logging APIs used by the renderer are supplied by that game
runtime. The optional Immersive Portals class-name probe is reflective and
copies no implementation.

The offline build uses an explicit allowlist of official game/loader library
groups from the local cache. The output contains only this project's classes,
metadata and authored shaders. Test fixture assets use vanilla blocks/entities;
no private player world or account files are distributed.
