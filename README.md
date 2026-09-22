# Psychiatryk Roles

Psychiatryk Roles is a Forge 1.20.1 server mod for a role-based Minecraft server. It assigns every new player the restricted **Konsultant** role, preserves privileged **Ordynator** and admitted **Pacjent** players, and provides controlled tools that grant narrowly scoped actions without removing the server's protections.

The mod also includes persistent one-time admission codes, bilingual Polish/English messaging, role prefixes in chat and the tab list, an unrestricted second overworld, a local operator command generator, and Dynmap-compatible dimension data.

## Requirements

- Minecraft 1.20.1
- Forge 47.4.10 or newer in the 47.x line
- Java 17 on the server

The gameplay logic and custom dimension use server-side code and vanilla registry data. The mod does not add custom blocks, entities, models, or textures.

## Roles

### Konsultant / Consultant

This is the default role for players who are neither operators nor admitted patients.

In the normal server worlds a consultant:

- stays in Survival mode while server-side event rules enforce Adventure-like restrictions;
- cannot break or place ordinary blocks without an authorized tool or amulet;
- cannot damage protected entities or other players;
- is treated neutrally by hostile mobs; a mob retaliates only against the consultant who provoked it, for 30 seconds;
- cannot trample farmland or mount entities and vehicles;
- has no player collision and receives continuous full hunger and saturation;
- can inspect containers, but cannot modify them without a `take` permission item;
- can use spruce blocks and interfaces, place/edit spruce signs, and remove those signs with the Sign Remover;
- receives one placeable spruce sign every ten minutes when none is present;
- may pick up owned mining drops, owned mob drops, XP, issued spruce signs, imported items, and anything authorized by a pickup token;
- may drop ordinary items, then recover those same dropped stacks;
- cannot drop or deposit protected consultant equipment.

Temporary ownership for ordinary tossed items, mining drops, and mob loot is stored on the dropped `ItemEntity`, including Minecraft's native owner UUID, rather than inside the `ItemStack` NBT. Drops belonging to the same player can merge in the world, different owners remain separated, and pickup preserves the item's original NBT so it stacks normally in inventory. Legacy stack ownership markers are still recognized and removed when encountered.

### Pacjent / Patient

Patients use normal Survival gameplay. The known legacy patient accounts remain patients. A consultant becomes a patient by redeeming a persistent, one-time admission code with `/przyjecie`.

Patients can use `/pacjent status` for a localized readout of their current dimension, coordinates, respawn point, experience level, and food level.

### Ordynator / Director

Minecraft operators are displayed as Ordynator/Director and retain unrestricted administrative behavior.

## Language selection and welcome

Every join displays:

```text
Użyj /polski dla języka polskiego lub /english for English.
```

For a new player, the default is selected from the connection IP country (`PL` gives Polish; other countries give English). If the lookup is unavailable, the Minecraft client locale is used. The choice is persisted. Players can override it at any time with:

```text
/polski
/english
```

Either command can be used repeatedly. Every use refreshes the current player's displayed item names and lore, updates their role label, and replays the consultant explanation. Localized names, lore, expiry lines, and book pages exist only in the packet copy sent to that viewer; the real item NBT stays language-neutral in inventories and containers. Returning consultants with an assigned language also receive the explanation after joining.
Choosing a language also gives the consultant a localized written handbook covering the role, protections, tools, travel, and core commands.

## Admission codes

Codes have the form:

```text
bip39word-2137-bip39word
```

The numeric component is one of `2137`, `69`, `420`, or `1337`. Codes are persistent and single-use.

Operator commands:

```text
/przyjecie-kod generuj
/przyjecie-kod generuj <count>
/przyjecie-kod lista
```

Player redemption:

```text
/przyjecie <code>
```

## Texas Hold'em poker

The mod includes a complete server-authoritative multiplayer poker loop presented through vanilla chat. It requires no additional client mod. Tables support two to nine funded players, dealer/blind rotation, preflop/flop/turn/river betting, check, call, total-bet raise, fold, all-in, side pots, tied pots with deterministic odd-chip assignment, and seven-card showdown evaluation.

Typical session:

```text
/poker gui
/poker create stol
/poker join stol
/poker exchange in [count]
/poker bank
/poker buyin <chips>
/poker bots add <1-4>
/poker start
/poker check
/poker call
/poker raise <total-bet>
/poker fold
/poker allin
/poker status
/poker cards
/poker cashout
/poker exchange out <item> [count]
/poker leave
```

Poker uses a separate persistent chip wallet. `/poker exchange in [count]` consumes a pristine accepted stack from the main hand and credits its configured value. `/poker exchange out <item> [count]` debits that same value and creates ordinary fresh items. Named, enchanted, damaged, NBT-bearing, and consultant equipment cannot enter the exchange. `/poker buyin <chips>` moves wallet chips to the current table; `/poker cashout` moves the whole table stack back to the wallet. The minimum first table buy-in is 100 chips and blinds are 10/20. This is a deliberately simplified ProjectE-style value system: conversions conserve configured value while bulk trash remains excluded.

`/poker gui` opens a six-row vanilla chest interface, so it requires no client mod. The lobby lists joinable tables and can create a personal table. At a table it renders community cards, private hole cards, seats, turn, pot, wallet, table stack, redeemable reserve, and clickable exchange-in, buy-in, cash-out, bot, start, check, call, raise, all-in, fold, and leave controls. Display items are read-only and never enter the player's inventory. Commands remain available.

For solo play, the table owner can use `/poker bots add <1-4>`. Bots are free, start with 100 house chips, use the normal validated turn/pot system, and play automatically. House chips are tracked separately from the redeemable reserve funded by human buy-ins. A cash-out can never exceed that reserve, so farming free bots cannot mint exchangeable items; excess house chips expire. `/poker bots remove` removes them between hands without paying their house stack to anyone. Bots and hands survive a process restart. Consultants can use both the GUI and commands without bypassing world protections.

Use `/poker values` for the authoritative in-game list. Common bulk items such as cobblestone and dirt are deliberately worth zero. Accepted per-item values are:

| Value | Items |
| ---: | --- |
| 1000 | elytra, nether star |
| 900 | netherite ingot |
| 600 | enchanted golden apple |
| 500 | totem of undying |
| 300 | heart of the sea |
| 250 | wither skeleton skull |
| 225 | netherite scrap |
| 180 | ancient debris |
| 100 | diamond |
| 90 | echo shard, Pigstep/Relic/Otherside music discs |
| 80 | shulker shell |
| 60 | nautilus shell |
| 40 | goat horn and the other listed music discs |
| 30 | emerald |
| 25 | dragon's breath |
| 20 | name tag, saddle |
| 18 / 9 | gold ingot / iron ingot |
| 3 / 2 / 1 | amethyst shard / gold nugget / iron nugget |

`/poker list` lists public tables and `/poker help` gives localized PL/EN instructions. Operators can run `/poker admin reset <table>` to cancel an active hand and refund every committed chip to its table stack. `/poker admin delete <table>` refuses to delete a table until all seats have left.

Wallets, tables, shuffled deck order, hole cards, board, turn state, commitments, table stacks, and bot sponsorship persist in `psychiatryk_poker.dat`. A disconnected player automatically checks when no payment is owed and otherwise folds when their turn arrives. On process restart, persisted human sessions are treated as offline while bots remain available; the first returning player triggers recovery until play reaches a connected seat or the hand settles.

## Permission items

Operators can turn any registered item into a marked permission item:

```text
/konsultant-item give <player> <item> <action> [targets]
/konsultant-item give <player> <item> <action> near <player|patients> <radius> [targets]
/konsultant-item give-timed <player> <item> <seconds> <action> [targets]
/konsultant-item give-timed <player> <item> <seconds> <action> near <player|patients> <radius> [targets]
```

Actions:

| Action | Behavior |
| --- | --- |
| `take` | Allows container modification while the token is anywhere in inventory. |
| `pickup` | Allows ordinary dropped-item pickup while the token is anywhere in inventory. |
| `attack` | Held tool may attack matching entities. |
| `mine` | Held tool may mine matching blocks. |
| `place` | Token in offhand allows the matching block in main hand to be placed. |
| `attack-amulet` | Inventory-wide attack permission. |
| `mine-amulet` | Inventory-wide mining permission. |
| `place-amulet` | Inventory-wide placement permission. |

Targets accept registry IDs, tags such as `#minecraft:logs`, comma-separated values, or `*`.

Built-in block presets:

- `rock`: stone, cobblestone, deepslate, andesite, granite, diorite, tuff, calcite, dripstone, blackstone, basalt, end stone, and netherrack
- `ores`: ore blocks and ancient debris
- `logs`, `dirt`, `sand`: the corresponding vanilla tags

Built-in entity presets:

- `hostile`, `passive`, `animals`, `villagers`, `players`
- `undead`, `arthropods`, `aquatic`, `illagers`
- `mobs`, `bosses`

Proximity conditions are active only while the named anchor is online, in the same dimension, and within range. The special anchor `patients` accepts any nearby patient.
Timed permission items persist across restarts, display their absolute expiry in lore, stop working at expiry, and are removed on the next player tick.

Examples:

```text
/konsultant-item give Alex minecraft:feather pickup
/konsultant-item give Alex minecraft:amethyst_shard attack-amulet hostile
/konsultant-item give Alex minecraft:flint mine-amulet near aridlin 16 rock
/konsultant-item give-timed Alex minecraft:amethyst_shard 3600 attack-amulet hostile
```

Ready-made presets avoid repeating the full syntax:

```text
/konsultant-item preset list
/konsultant-item preset give <player> <preset>
/konsultant-item preset give-timed <player> <preset> <seconds>
```

Available presets are `pickup`, `container-key`, `hostile-amulet`, `rock-amulet`, `sign`, `sign-remover`, `sword`, `pickaxe`, `importer`, `extractor`, `passage-staff`, and `return-mirror`.

Remove marked items from an online or offline player's inventory and Ender Chest:

```text
/konsultant-item clear <player-name>
```

The clear operation recognizes explicit consultant subtypes rather than trusting a generic marker, and creates a timestamped backup of available player data before mutation.

## Status and audit log

Consultants inspect their effective permissions with `/konsultant status`. Operators can use `/konsultant status <player>` for another online player. Output includes role, language, world mode, targets, proximity state, and remaining expiry.

The latest 1,000 audit events persist in world data and are also written to the server log. Operators can view them in game:

```text
/konsultant-log
/konsultant-log page <page>
/konsultant-log player <name> [page]
/konsultant-log clear
```

The log covers login and role state, language changes, admission codes, administrative item grants and clears, travel, expirations, hostile provocation, and blocked destructive actions. Repeated identical denials are debounced for two seconds.

## Recipes and utility items

Protected recipes are intended to be crafted by patients or directors and handed to consultants. Consultant crafting attempts are rejected, except for the Recipe Book, Mirror of Returning, Escort Compass, and Cleanup Bag.

- **Consultant Sword:** two sticks above one cobblestone; attacks hostile mobs only.
- **Consultant Pickaxe:** three sticks across the top, then two vertically centered cobblestone; mines stone and cobblestone.
- **Extractor:** five sticks in a plus; removes marked consultant equipment.
- **Importer:** eight sticks in a ring; right-click with an ordinary item in the offhand to mark and move the stack.
- **Passage Staff:** three vertical sticks; right-click or drop it to change between the restricted world and the consultant world.
- **Consultant Recipe Book:** one stick in any crafting slot; opens a localized illustrated-by-text guide to every built-in recipe and its use.
- **Escort Compass:** one compass plus string; points to the nearest online patient in the same dimension and reports their distance.
- **Temporary Chalk:** white dye plus a stick; marks the distant block in the crosshair with a long-distance, three-dimensional particle X, a ten-second cooldown, five-marker cap, and persistent 24-hour expiry. Punching a block or entity with the Chalk removes the owner's oldest marker without damaging the target.
- **Cleanup Bag:** five leather plus string; recalls the holder's currently loaded dropped, mined, and mob-loot entities, removes temporary ownership metadata so ordinary items stack normally, and places inventory overflow at their feet.

The **Mirror of Returning** is an ordinary echo shard produced from one glass block over one stick. Consultants may craft it. One use teleports to a valid personal respawn point, falling back to overworld spawn. A second use within three seconds goes directly to overworld spawn.

## Consultant world

`psychiatryk_roles:konsultanci` is a second, independent overworld generated with vanilla overworld noise and biomes. The Passage Staff stores a separate last position on each side. First entry uses the dimension spawn.

Consultants remain in Survival and have unrestricted building, combat, containers, vehicles, item pickup, and ordinary item dropping there. Protected consultant equipment remains controlled.

Dynmap discovers the dimension as `psychiatryk_roles_konsultanci` and generates its surface, cave, and flat map layers.

## Sleep behavior

Consultants in the restricted overworld cannot enter beds and are excluded from the required sleeper count. The original `playersSleepingPercentage` value is retained as the base for eligible non-consultant players.

## Local command generator

[`docs/index.html`](docs/index.html) is a dependency-free command builder and operator reference. It includes action selection, proximity conditions, presets, recipe diagrams, and maintenance notes.

The public server information page source is kept in [`website/index.html`](website/index.html). Its **Przedmioty** tab documents every built-in recipe and the **Generator** tab provides the same command builder in the site's paper-and-ink design. The entire page can be switched between Polish and English using the visible **PL** and **EN** buttons. A manual choice is stored in browser local storage. Without one, the page applies a browser-language and `Europe/Warsaw` timezone fallback immediately, then asks `ipwho.is` only for a country code with credentials, referrer, and caching disabled. Failure or blocking of that lookup leaves the local fallback in place.

## Pre-login connection guidance

Forge 47.4.10 rejects a vanilla client inside `ServerLifecycleHooks.handleServerLogin`, before Minecraft creates a login player and before normal Forge player/login events can run. The mod therefore uses a required server-side Mixin on that exact Forge hook and replaces both early rejection strings:

- a vanilla/no-Forge client is directed to `info.goplanska.pl` for Forge 1.20.1 and the modpack;
- an incompatible Forge network version is directed to the same setup page.

The channel list in the server log identifies required mods that rejected a vanilla connection, but those mods do not compose the final vanilla-client disconnect text; Forge centralizes that text in `ServerLifecycleHooks`. Later FML handshakes are different: a Forge client with a mismatched mod/channel list receives structured mismatch data and can render its own client-side incompatibility screen. A server-only mod cannot replace UI text rendered by an unmodified client. The public setup address is therefore guaranteed at the earliest server-owned rejection, while later client-owned mismatch presentation remains bounded by Forge and the client mod loader.

Run it directly:

```bash
python3 -m http.server 8765 --bind 127.0.0.1 --directory docs
```

Or install the included systemd user service:

```bash
chmod +x tools/install-local-generator.sh
./tools/install-local-generator.sh
```

Then open <http://127.0.0.1:8765/>. To start the user service at boot before login, enable lingering once:

```bash
loginctl enable-linger "$USER"
```

## Build

Use Java 17 and Gradle 8.10.2:

```bash
gradle --no-daemon test build
```

The reobfuscated server JAR is written to:

```text
build/libs/psychiatryk-roles-1.0.0.jar
```

## Installation

1. Stop the Forge server.
2. Back up the world and the previous mod JAR.
3. Copy the built JAR into `mods/`.
4. Start the server and confirm `Done` appears without a `psychiatryk_roles` load error.
5. Verify `/help konsultant-item`, `/help przyjecie`, and `execute in psychiatryk_roles:konsultanci run time query daytime`.

Persistent role, code, language, travel-position, audit-log, and sleep-base data is stored in the overworld saved-data file `psychiatryk_roles.dat`. Poker wallets, tables, hands, table stacks, and bots are stored separately in `psychiatryk_poker.dat`.

## Safety notes

- Keep a rollback JAR before each live replacement.
- Do not run inventory-clearing commands as a smoke test on real players.
- The local generator binds to loopback only.
- Server credentials, RCON passwords, SFTP details, and player data do not belong in this repository.
