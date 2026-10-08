# Live item unlocks (8 October 2026)

A small server-side companion to Roles 3.0.8 and the queued 3.0.9. It replaces the hard-coded workstation policy through two required Mixin hooks, without changing the Roles JAR or its queued updater. No custom client packets, screens, or item registrations. Exclude `/mods/psychiatryk-unlocks-*.jar` from AutoModpack with a `!` rule.

## Calendar

Dates start at midnight Europe/Warsaw, with the October daylight-saving change respected. Florist, Woodworker, Iceman and Hunter: October 8. Engineer: October 10. Miner: October 12. Netherologist: October 14. Oceanographer: October 16. Gilded Station: October 18 (no Ascended trade definition exists in the installed MoreVillagers Re version). Enderologist: November 1. Explorer uses the decorated pot and remains available.

## Operator menu

`/itemlocks` opens a paginated catalog of every registered item; configured items appear first. `/itemlocks search <ID fragment>` filters it, `/itemlocks held` selects the held item, and `/itemlocks status` reports locks and real recipe presence to console. Only permission level 2 or above may open or edit the menu. Click an item to allow now, block indefinitely, choose tomorrow / two days / November 1, or increment/decrement its date. Menu icons never transfer to player inventory; shift, swap, drag, drop and double-click are rejected. Permissions are rechecked on every click.

`config/psychiatryk-item-unlocks.json` persists edits atomically. The server checks calendar state each second and serializes asynchronous recipe reloads when availability changes. A failed reload is retried. Restrictions cover normal output-based crafting recipes, survival use, placement, pickup, held-item attack/mining, and interaction with locked villager professions. Creative retains the existing bypass. This is not a claim to intercept every third-party machine recipe or remove existing equipped items. Items are never deleted.

## Website

`website/index.html` adds the Polish ink-style timeline to the then-current homepage. It reads `item-unlocks.json` each minute and uses Europe/Warsaw calendar dates. `website/item-unlocks-update.php` accepts authenticated POSTs only and publishes a sanitized atomic JSON snapshot. Apache/PHP deployments that strip Authorization must forward it only for this endpoint (see `website/htaccess-snippet.conf`). Its random token lives outside the web root at `../private/item-unlocks-token`. The server reads matching `url` and `token` from `config/psychiatryk-unlocks-publish.properties` (not distributed to clients or committed). HTTP work is asynchronous and bounded to one request with a ten-second timeout. No player data is transmitted. A five-minute freshness warning distinguishes an old calendar from a live server confirmation.

## Build and tests

`python build.py` requires local Java 21 and the NeoForge 1.21.1 development cache. Compilation uses a 384 MB heap. RulesTest uses an isolated working directory; 39 checks cover all calendar boundaries, November DST, Hunter, arbitrary item changes, invalid dates, and persisted state. The native fixture loads the actual production MigrationPolicy.class in an isolated NeoForge 21.1.252 server (1.2 GB heap, two processors), applies real Sponge Mixin, checks recipe decisions, live arbitrary-item edits, command registration, revoked operator permissions, and all non-pickup menu click types. Fixture tests are not a player-visible GUI claim.

Rollback: stop the Minecraft server, move the companion JAR out of mods, and restart. Original Roles policy then applies. Preserve the policy JSON for later restoration.
