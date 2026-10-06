# S23 v31

Client rendering optimization: skip offscreen entities in the chams occlusion pass, using the main camera frustum and a conservative two-block animation/movement margin. Wall-occluded visible entities are still drawn to the cover buffer; self-occlusion exclusions are preserved. Only ChamsPass, its nested Cache class and ChamsEffect changed relative to v30. Server gameplay and protocol are unchanged, so no production server restart was needed.

The isolated real-GL test rendered 64 cows: eight ahead, 56 behind the camera. Filtering reduced the cover pass from 64 to eight model submissions. The composited framebuffer matched byte-for-byte. Mean submission CPU time: 1.243 → 0.469 ms. Mean GPU time: 0.301 → 0.087 ms. These are timings of this pass, not a claim about total game FPS or the user's actual base.

The tested candidate checksum is in runtime-report.json. Existing v30 headlight, sound, wall-kick and bound marker fixes are preserved. Production diagnostic logs and player data are private and excluded.
