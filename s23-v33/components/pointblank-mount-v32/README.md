# PointBlank shots while riding a scooter

Target: PointBlank NeoForge 1.21 2.2.0. Java 21. The changes are optional common mixins (`@Pseudo`) with exact installed method descriptors and required injection counts. Register `psychiatryk-pointblank-mount.mixins.json` in the unified mod's NeoForge metadata.

## Evidence and change

PointBlank's two full `HitScan` methods query entities while excluding only the exact shooter. The rider's scooter and co-passenger can therefore intercept the shot or be accepted by the server's supplied-target validation. `SlowProjectile` can replace its normal collision result with a cached near-owner target, bypassing its usual collision predicate.

The patch removes the rider's own scooter assembly from the hitscan query and supplied-target validation. It also rejects that assembly in slow projectile collision and clears an unsafe cached assembly hit before normal collision is evaluated. The filter only applies when the shooter's root vehicle is our `Scooter`. It preserves outside targets, parked scooters, other vehicles and explosive splash behavior.

This establishes and fixes own-mount obstruction and co-passenger targeting. It does not establish direct mount-to-shooter damage: the existing scooter is invulnerable and does not forward damage to its rider, while PointBlank already excludes the exact shooter.

## Verification

`runtime-report.json`: 14 checks passed on a private dedicated NeoForge server with the actual installed PointBlank and applied mixins. The fixture creates a real scooter, mounted driver and co-passenger, and an outside target. It tests both actual hitscan methods, projectile collision predicates, the cached-target bypass, ordinary dismounted behavior and unchanged horse behavior. A normal ServerStopped event and save lifecycle completed. No production or main client was touched.

`build-proof.json` contains exact three class hashes, resource hash, source hashes and the actual tested private jar hash. Only these classes and the resource belong in the release. The QA helper is private fixture code. The audit bytecode/decompiled upstream materials are private and must not be redistributed.

The fixture's initial unsuccessful attempts were corrected without changing patch logic: the first package layout put a helper in a mixin package; subsequent geometry failures used persisted test entities or a ray above the scooter's bounding box. The final package and actual query geometry are covered by the successful report.
