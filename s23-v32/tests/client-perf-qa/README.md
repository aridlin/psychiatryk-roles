# Title-screen client performance QA

Test-only source: use a disposable client and the exact v32 JAR/NeoForge stack.
Do not ship the helper in the modpack. It generates synthetic payloads and
executes client commands at the title screen; it loads no world or server.
Configure the test client's Sound Physics file with:

    environment_evaluation_ray_count=24
    environment_evaluation_ray_bounces=2
    max_occlusion_rays=8

Retain update_moving_sounds=true, sound_update_interval=5,
sound_direction_evaluation=true, unsafe_level_access=false, and the existing
wall occlusion/smoothing settings. The helper verifies capture suppression only
for snapshot-ineligible idle PLAY payloads, retention of snapshot-eligible and
configuration payloads, unchanged buffer offsets/reference count, runtime
reflection precedence, /chams scooter off|on persistence and other categories.
Recording/paused-recording preservation is part of the separate predicate proof.

build.py is the workspace compiler recipe; provide its exact release dependency
paths locally. Set the helper's report output path for your environment before
building. The private Prism clone, account cache, launcher and game logs are
excluded; runtime-report.json contains only synthetic check outcomes.
