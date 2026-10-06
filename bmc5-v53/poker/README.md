# Poker: persistent server-wide escrow

This overlay replaces the existing Poker Java classes and adds `data/psychiatryk_roles/poker/official-emc.json`. Keep the existing Roles constructor hooks for `PokerMenus.register`, command registration, item interaction and periodic poker bot updates. Code retains the existing `psychiatryk_roles:poker` menu ID and 54-slot client/server contract.

## Financial rules

* 1 redeemable credit = 1 EMC. The table uses the pinned official ProjectE `mc1.21.1` base/tag values recorded in `research/provenance.json`; no ProjectE gameplay mod is installed.
* The price resource contains 193 direct item values and 32 official tag values, with a small documented set of vanilla recipe arithmetic (for example ingot/nugget conversions). It is not the complete ProjectE recipe mapper. Unsupported items are refused rather than given invented prices.
* Deposits move actual items into globally shared stock. Each lot stores an exact ItemStack prototype (including original names, damage, enchantments/components), a count and its per-deposit EMC value. Identical prototypes at identical prices merge; distinct components remain distinct.
* A withdrawal spends the selected lot's frozen value and returns copies of its exact original components. It cannot create stock that was not deposited. An item-ID command selects an actual lot; the GUI selects a stable lot ID.
* The inventory insertion plan is computed before changing wallet or stock. Insufficient money, stock, room, or overflow leave all inputs unchanged. Withdrawals never use a world-drop fallback. GUI stock icons cannot be extracted, cloned or swapped; their explanatory lore never enters the stored ItemStack.
* Wallet + redeemable table reserves may not exceed stock value. Free bots add only house chips. Their winnings cannot create new redeemable money, and a bot cashout cannot consume the redeemable reserve.
* Old pre-escrow wallet balances are retained as `LegacyWallets`, not spendable value; old table reserves become zero. No items are invented to back old balances. New BMC worlds have no legacy balances.

The bank is persisted in the overworld saved-data file `psychiatryk_poker.dat`. Transactions are atomic within the server tick and survive ordinary saves/restarts. This is not a new write-ahead journal spanning player NBT plus world saved-data, so it does not promise all-or-nothing persistence across an abrupt machine/power failure during unrelated save operations.

## GUI

`/poker gui` or the poker clock opens the existing poker lobby/table. The emerald action opens **Deposit / cash out items**. The upper grid shows real server stock, counts, per-item EMC and original item names/components. Click withdraws one; Shift-click withdraws up to one stack. Inventory icons below deposit one on click or the complete stack on Shift-click. The hopper deposits the main-hand stack. Previous/next/back/close are separate actions. Inventory room failures do not charge the wallet. The right sidebar explains this contract.

## Verification and public source allowlist

`test.py` / `PokerEscrowQA.java` operate on actual Minecraft ItemStack, registry-aware NBT serialization and actual PokerGame reserve logic. They include 1000 random deposit/withdraw transitions with item/EMC conservation checks. `runtime/runtime-report.json` is the real dedicated-server ServerPlayer/PokerMenu transaction fixture; its only shim is existing Roles language/audit functions. No copied world or player data is part of the public helper.

Public-safe: `src`, `resources`, this README, `build.py`, `test.py`, `PokerEscrowQA.java`, sanitized reports/provenance and `runtime/src`, `runtime/resources`, `runtime/build.gradle`, `runtime/settings.gradle`, `runtime/gradle.properties`. Exclude `runtime/run`, `runtime/build`, all worlds/logs, cache manifests, classes and any local launch/account data. Do not redistribute the whole private `research/official-tree.json` download; provenance plus the authored price resource suffice.
