# Isolated save-I/O QA

Use Java 21 and the exact NeoForge 21.1.252 / Sable 2.0.5 mod stack from
the release manifest, with a disposable server/world. The helper creates two
invented FakePlayers and a two-block test assembly; no production data is here.
build.py builds the test-only mod and fault-injection mixins. Supply the local
server launcher run.py expected by run_case.py and install the tested candidate
only for candidate cases. Use the unchanged v31 JAR for the baseline case.
Do not install this QA mod on a production server: it injects delays/failures.
Check the runtime report for the cases actually executed and their results.
OwnerFailureQA and OwnerBoundQA exercise the ordered worker independently.
The production music library, server/world exports and credentials are excluded.
