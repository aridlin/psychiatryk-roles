# Corrected Roles 3.0.8 client loader and normal crop growth

The original JEI and Flywheel compatibility plugins called the actual Sponge ModLauncher class provider with an unsupported flag. This repair uses the supported `getClassNode(name, true, 0)` API and skips self-transformation. The corrected addon changes exactly those two plugin classes; common classes, protocols, recipes, assets and version metadata are byte identical to the original 3.0.8 build.

The authored source, native fixtures and portable builder are in [overlays/loader-compat-3.0.8](overlays/loader-compat-3.0.8/README.md). The native proof reproduces the old failure and tests legacy/fixed/unknown/absent targets. The exported builder reproduces corrected SHA-256 `56cc13966c2ed202ff54ac9e0b401fce32325842d93fff2be22b1ee14de9b614` byte identically.

[Actual activation/launch evidence](patch-evidence/launch-loaderfix-3.0.8.json) separately records the hot four-path publication without restarting the server, pinned native AutoModpack delivery, and the corrected full client loading and joining successfully. Future pre-launch account fallback is not claimed retested; raw client logs and account data are omitted.

`config/sereneseasons/fertility.toml` sets `seasonal_crops=false`, so crops use normal seasonal growth behavior in every season. The paired server/host config was applied, the native feed regenerated and the actual hosted configuration downloaded with pinned TLS. All other TOML values and randomTickSpeed remain unchanged. The verifier did not directly measure the server's live singleton field or a crop growth rate; the ordinary next join/rejoin configuration handshake reloads the value through the verified native bytecode path.

Fresh addon and bootstrap installer preparation selects the corrected JAR and includes the exact canonical LF crop config. The previous published downloads and source build remain historical. The fresh website packages were separately published with the page renamed last, after all nine files passed SFTP and HTTPS hash readbacks. The sanitized receipt records those actual public checks; no public binary download is included in this source checkout.
