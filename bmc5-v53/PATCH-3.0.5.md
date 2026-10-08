# Prepared Roles 3.0.5 — Peeb and music

This is source preparation, not a production activation receipt. The server,
AutoModpack feed and current download installers require a coordinated release
because 3.0.5 adds common-side Peeb registration and packet types. Native tests
run in an isolated Better MC profile. A successful source build does not prove a
public join, dedicated startup, player feel or production performance.

## Implementation

- Hold Peeb or wear it in the chest slot with the other armour slots empty.
  The character uses the Peeb rig, animation, tusk attachment, third-person
  camera, landing/step effects, seven natural armour and fall-damage immunity.
- Camera targeting uses the same aim ray on client/server. Looking fully up/down
  is allowed. Optional keyboard bindings yield to other registered bindings;
  the inventory key remains available. Freelook prefers Left Alt and uses a
  free alternative when that key is occupied. The default camera is raised to
  keep Peeb below the targeting line.
- Grapple reels toward a two-block rest length with a quick winch and a
  nonelastic taut-rope constraint. In the actual-class ground fixture, it arrives
  near the anchor in16 ticks (0.8seconds); this is not a human-feel result.
  Slack never pushes outward, sideways momentum is retained and grounded floor
  anchors do not add downward pull. Owner-client physics runs once per 20 Hz
  tick. The server reels and validates, rather than adding a second force.
- The thick tusk rope has 24 curved segments. Slack sags to match synchronized
  rest length; tension straightens it. Endpoints are the animated tusk tip and
  authoritative anchor.
- Local Peeb pixels in front of the targeting point use an ordered dither
  cutout, letting the depth-tested ring remain visible through the local model.
  Other Peebs stay opaque. Shader-compatible chams keep scoped shader support.
- Jukebox recovery, favorites, next-track autoplay, shuffle and category selection
  are in the retained checkpoint. Playback metadata drives a new-track overlay,
  persistent HUD and cover art on jukebox vinyl/back art. Public YouTube/YT Music
  import requests use a bounded configured API; playlists retain source order.
- Scooter batteries use throttle and can gain charge while their owner is offline
  with them in inventory. Rare zombie rentals leave the rental on death.

## Build and evidence

[Build/context](overlays/peeb-music-3.0.5/README.md) documents the saved 4173
checkpoint, reconstructed source and six-family exact overlay. The final
f03a candidate has 16 changed and two added archive entries, with 664 existing
entries unchanged. No runtime classes are generated from untouched recovered
music/scooter/chams source.

Focused math checks: [8,218 physics checks](patch-evidence/peeb-rope-3.0.5-math.json),
[28,513 curve checks](patch-evidence/peeb-rope-3.0.5-geometry.json) and
[ten event/payload bytecode checks](patch-evidence/peeb-rope-3.0.5-timing.json).
[Build proof](patch-evidence/peeb-rope-dither-3.0.5-build.json) is explicitly not
native runtime qualification.

<!-- NATIVE-QUALIFICATION -->
The [focused native gate](patch-evidence/peeb-rope-dither-3.0.5-native.json) passed
195 assertions on the exact f03a1a28 candidate, including real E inventory
input, separate local/remote shader draws, flexible-rope vertices/endpoints,
quick pull, camera placement, floor support and cleanup. It used software OpenGL
and shaders off; no new shaderpack-on, full music, production or human-feel
qualification is inferred.
<!-- /NATIVE-QUALIFICATION -->

All former recipe grids and the detailed scooter model remain checkpoint bytes.
The separate portable modules and old root project are preserved. No original
Peeb/game assets or private music library is made reusable under the code license.
