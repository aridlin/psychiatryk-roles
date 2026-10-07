# Roles 3.0.1 patch — production activated

Production activation was verified at 2026-10-07 08:06:53 UTC after the
owner-authorized restart. Server and AutoModpack-hosted addon hashes match the
final candidate. All nine reviewed files, the fresh completed boot, TLS/status
response and 1,127-file / 353-mod feed were verified. The original JEI and
Distraction Free Recipes bytes were retained. See the
[sanitized activation receipt](patch-evidence/production-activation.json).
The patch adds separate seeded normal/flat testing worlds, a shared isolated
test inventory bank, URL music and Shift-jukebox search, and fixes mask geometry
leaking into ordinary Iris world batches. Sophisticated Backpacks/Core are
external official mods pinned in patch-evidence/official-backpack-versions.json.
The original BMC JEI and Distraction Free Recipes are retained.

The frozen merged candidate SHA-256 is
`f510fa2dd1b5bc111277315b5323bbff21d95f2f4f9e60618b7a2892c9e43239`.
The server now selects this release. The importable installers resolve official
AutoModpack and fetch whichever release the server selects.

## Dragon eggshell shard texture

The shard now has its own transparent 32×32 black/deep-violet eggshell sprite,
with a purple fractured inner rim. Its item model uses
`psychiatryk_roles:item/dragon_egg_shard` instead of the vanilla amethyst sprite.
The model and PNG are the exact final JAR resources in the
`src/egg-shard-texture/main/resources` overlay. The
[generation prompt](egg-shard-texture/prompt.txt) and
[public provenance](egg-shard-texture/provenance.json) document built-in imagegen,
alpha preservation, nearest-neighbor scaling and visual inspection.

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
Guard531 inherits all 371 pre-existing f6 classes and every resource except the
server mixin registration. Final f510 then inherits all 372 guard531 classes;
only the inspected shard PNG and item model change. The music, inventory
bank and native downloader receipts still identify f6. The renderer receipt
identifies cc78; the f6 and final inheritance receipts link its unchanged bytes.
The collector records 307 inherited component checks. Those runtime gates were
not rerun after the asset-only update; no f510 native/client/render cycle is claimed.
See [the inheritance receipt](patch-evidence/final-candidate-inheritance.json),
[the guard531 inheritance receipt](patch-evidence/guard531-candidate-inheritance.json),
[the 307-check collector](patch-evidence/final-verification.json),
[the asset build receipt](patch-evidence/egg-shard-asset-build.json),
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
