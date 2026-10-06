# S23 v29

Client hotfix: scooter headlights recover their LambDynamicLights registration after registry resets, and use the player's world for lifetime checks during Immersive Portals rendering. Looping scooter WAV playback queues the next sound instead of adding to the active sound list during SoundEngine iteration.

Isolated dedicated-client tests cover the portal world switch, registry reset, rendered light-on/off comparison and repeated WAV playback through the real sound engine. Main player confirmed visible headlights. The local rider remembers impact momentum for seven ticks, consumed once on a wall kick. Headlight and audio fixes are client code. Network protocol unchanged. Production hotfix required no server restart.

Private diagnostics, credentials, player logs and user music library are excluded. Bundled procedural audio and component licensing are preserved.
