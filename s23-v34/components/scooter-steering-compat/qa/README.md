# Dedicated scooter/spear QA

The authored helper creates only synthetic players and scooters in a disposable flat world. It never joins production or uses an actual account. It invokes the installed spear mod's real `PiercingWeapon.stab` and compatibility `TriggerStabEffectsC2SPacket.trigger` methods on the exact candidate.

28 checks include registered spear/enchant data, normal and compatibility hooks, synchronized 100 km/h target and 40-tick taper, original level II/III durability and exhaustion costs once, no direct rider impulse, and rejection without extra costs during cooldown, empty battery, absent enchant, hunger cutoff, water, rental, co-passenger and already-high speed. The original unmounted player Lunge remains intact. Dedicated initialization also exercises the common steering helper with Shoulder Surfing absent. One final check verifies the actual Soul Speed III terrain speed cap stays constant across 100 evaluations and returns to the ordinary limit off soul ground or airborne.

Build/setup: `python build_setup.py /absolute/path/to/candidate.jar`. Run: `python run.py`. The fixture uses only loopback game/query port 25599 and voice port 25600. It stops through the normal server lifecycle, then cleans any remaining native worker. The source fixture's libraries and mod jars are reused read-only, while config/world copies belong only to this test.

Publication-safe: `src/`, `build_setup.py`, `run.py`, this README, and the sanitized `runtime-report.json`. Exclude `server/`, private console/crash logs, helper binaries, cached player profiles and all copied configs/worlds. The report contains only check descriptions/results and the candidate hash. Actual client camera/model checks are covered separately by the parent's disposable rendering fixture.
