# 3.0.13 isolated QA result — 9 October 2026

Candidate SHA-256: `9d8c6b62c17d4c20b67ea3d208c5adef0a150a899433fb5fe825c0b63e439bed`.

The build changed exactly three Java classes and the mod metadata from the verified live 3.0.12 JAR. ASM verification passed for all changed classes, the archive round-tripped without duplicate entries, and no class retained a reference to the old mixin-owned `music/MusicAudibility` package. The old and new `audible(DFFFIZZ)Z` method signatures match.

The first isolated full-pack dedicated-server run with the combined candidate used 299 JARs, reached `Done`, printed `SPYGLASS_CHECK PASS list + vanilla item + consultant tag + expiry + air/block use`, and stopped cleanly with world saves. It bound Minecraft to `127.0.0.1:25596`; the QA services were disabled. The supervisor's status was `passed=true`, `clean_stop=true`, `exit=0`.

A separate Prism instance on the CachyOS PC launched Minecraft 1.21.1, NeoForge 21.1.252, and 462 reported mods on private Xvfb display `:96`. This instance used a 9 GiB maximum heap and the candidate JAR hash above. The authenticated `aridlin` client joined the isolated loopback server at 10:22 CEST. After placing a jukebox, the client opened its custom music GUI and played `Minecraft - A Familiar Room` from the server library at 10:27:56. The client stayed connected without `IllegalClassLoadError` until the deliberate QA kick at 10:32:46. `evidence/jukebox-playing.png` records the playing UI; the client's test-instance crash-report directory remained empty.

The server console successfully ran `konsultant-item preset give aridlin spyglass`. After returning to the Consultant's enforced Survival mode, a fresh tagged spyglass zoomed in the client while right click was held; `evidence/spyglass-zoom.png` records the result. The command was logged as `PRESET_GIVE` on the QA server.

The test server received `stop` and saved its world. The isolated client/Prism/Xvfb processes were closed, and the temporary local QA copy of `accounts.json` was removed. The user's existing Prism instance and live server were not modified by this QA.

This checks one built-in library track through the custom jukebox. It does not establish that external YouTube/Spotify imports or all third-party tracks work, and it is not a production-client rollout test.
