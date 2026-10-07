# Prepared Roles 3.0.1 patch

This export describes a prepared patch; it does not claim production activation.
The patch adds separate seeded normal/flat testing worlds, a shared isolated
test inventory bank, URL music and Shift-jukebox search, and fixes mask geometry
leaking into ordinary Iris world batches. Sophisticated Backpacks/Core are
external official mods pinned in patch-evidence/official-backpack-versions.json.
The original BMC JEI and Distraction Free Recipes are retained.

The frozen merged candidate SHA-256 is
`5316437d3921aba2296a540b35797fc9ab874f2228d89009f381b44b9b343d00`.
The public current-addon ZIP still contains 3.0.0 so users do not install a
different music protocol before the server update. The importable installers
resolve official AutoModpack and fetch whichever release the server selects.

## Poker inventory isolation

On the dedicated server, poker item deposits and withdrawals are disabled in
both testing dimensions. This prevents creative test items from entering the
ordinary-world escrow. Use `/testworld back` to restore the ordinary inventory
before exchanging items. The guard covers command and menu routes, including
single and Shift clicks. Existing wallet/table chips can still be used for poker.
The mixin is registered in the `server` list; an integrated-server guard or a
new client network-message delivery test is not claimed.

## Evidence and inheritance

The final guard passed its dedicated full-BMC server fixture. The frozen build
receipt was created before that runtime gate, so its `runtime_verified: false`
field is retained; the separate runtime receipt records the actual result.
All 371 pre-existing f6 classes and every resource except the server mixin
registration are byte-identical in the final candidate. The music, inventory
bank and native downloader receipts still identify f6. The renderer receipt
identifies cc78; the f6 and final inheritance receipts link its unchanged bytes.
Those client/downloader/render gates were not rerun after the server-only guard.
See [the inheritance receipt](patch-evidence/final-candidate-inheritance.json),
[the guard build](patch-evidence/poker-bank-guard-build.json),
[the guard runtime](patch-evidence/poker-bank-guard-runtime.json),
[the final qualification](patch-evidence/poker-bank-guard-qualification.json), and
[the frozen native update receipt](patch-evidence/native-update-f6.json).

Patch sources are authored overlays, not a complete redistributed Better MC
modpack. Testing-world source depends on the reduced Roles core; portable
scooter/chams remain separate minimal-dependency source modules. Private runtime
fixtures, audio, catalog, player/world/account files and launcher helpers are
excluded. Evidence distinguishes the tested renderer component from the final
combined candidate, which inherits the exact same renderer bytes.
