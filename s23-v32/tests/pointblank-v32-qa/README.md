# Isolated scoped-weapon rendering QA

Use only a disposable Java 21 / NeoForge client with the exact release mods.
The helper alters its private test world and inventory and observes allocator,
framebuffer, scope and GUI rendering. Do not install it in a production pack.
Supply the release dependencies locally; upstream jars and decompiled source
are not included. Set the report output path before building. The report lists
the exact observed phases: an allocator-only pass is not a complete scope test.
Private launcher state, account caches, worlds, screenshots and logs are excluded.
