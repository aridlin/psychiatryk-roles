## v33 scooter controls
Keyboard steering follows the requested direction reliably, with low-speed
turning bounded by curvature instead of spinning in place. High-speed turning
keeps its existing yaw limit; the steering model is bounded to ±28 degrees. Third-person
control is corrected. Overspeed returns linearly toward cruising speed over
5 seconds. Direct model animation layers preserve the full steering angle,
including shared handlebar/hand poses. Soul Speed raises the terrain cap once
instead of multiplying speed each tick. A spear's own Lunge triggers one 100 km/h boost with a 40-tick taper;
it requires that spear enchantment. Mouse steering and existing upgrades remain.
The build proof lists the exact changed classes and optional helper additions;
every other existing JAR entry is byte-identical to v32. Runtime reports describe the cases actually exercised, not every terrain,
network or input situation. Keep the v32 pack and activation backup for rollback.
