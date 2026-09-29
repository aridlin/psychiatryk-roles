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

### Kontraktor / Contractor

A Pacjent (or an operator) can hire an online consultant with `/zatrudnic <nick>` and remove the assignment with `/wyjebac <nick>`. Contractors receive Patient gameplay while their condition is active. They keep the Kontraktor label; when the condition is false, Consultant restrictions apply. Contractors cannot hire or fire other players. Assignments and conditions persist in `psychiatryk_roles.dat`.

The default condition is always active. A Pacjent can configure each online contractor independently:

```text
/kontraktor <nick> zawsze
/kontraktor <nick> online <pacjent|dowolny>
/kontraktor <nick> blisko <pacjent|dowolny> <promien>
```

`online` requires the selected Pacjent, or any Pacjent with `dowolny`, to be online. `blisko` additionally requires the Pacjent to be within the chosen radius in the same dimension. A named Pacjent must be online when the condition is configured; the rule then stores their UUID, so name changes do not break it. The active state updates within one second. `/pacjent status` is available while the contractor has Patient gameplay.

For FTB Ranks integration, add a `kontraktor` rank with the same gameplay permissions as the `member`/Pacjent rank. The mod adds this rank while the contractor condition is active and removes it when inactive or fired.

The first deployment migrates Kameleon1200 from Pacjent to an always active Kontraktor. The migration runs once and preserves the other Pacjent accounts, including rozowykocurek.

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

Craft the poker menu clock from **one clock + one paper** in any crafting slots, or use `/poker gui item`. Operators/RCON can use `/poker gui item <player>`; offline deliveries are saved until the next login with free inventory space. The active seat wears a pulsing golden helmet, and the top-left book contains the viewer's latest poker chat messages. Private messages stay private to that viewer.


The mod includes a complete server-authoritative multiplayer poker loop presented through vanilla chat. It requires no additional client mod. Tables support two to nine funded players, dealer/blind rotation, preflop/flop/turn/river betting, check, call, total-bet raise, fold, all-in, side pots, tied pots with deterministic odd-chip assignment, and seven-card showdown evaluation.

Typical session:

```text
/poker gui
/poker gui item
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

Poker uses a separate persistent chip wallet. `/poker exchange in [count]` consumes a pristine accepted stack from the main hand and credits its configured value. `/poker exchange out <item> [count]` buys only basic materials: iron or gold nuggets and ingots, amethyst shards, and emeralds. Rare items can be exchanged in but cannot be bought back. Elytra has no poker value in either direction. Named, enchanted, damaged, NBT-bearing, and consultant equipment cannot enter the exchange. `/poker buyin <chips>` moves wallet chips to the current table; `/poker cashout` moves the whole table stack back to the wallet. The minimum first table buy-in is 100 chips and blinds are 10/20.

`/poker gui` opens a six-row vanilla chest interface, so it requires no client mod. `/poker gui item` gives a clock that opens the same menu on right-click. The lobby lists joinable tables and can create a personal table. At a table it renders community cards, private hole cards, seats, turn, pot, wallet, table stack, redeemable reserve, and clickable buy-in, cash-out, bot, start, check, call, raise, all-in, fold, and leave controls. The exchange button in both lobby and table opens a catalog: sell the full main-hand stack, click an item to buy one, or shift-click to buy a full stack. Display items are read-only and never enter the player's inventory. Commands remain available.

For solo play, the table owner can use `/poker bots add <1-4>`. Bots are free, start with 100 house chips, use the normal validated turn/pot system, and play automatically. House chips are tracked separately from the redeemable reserve funded by human buy-ins. A cash-out can never exceed that reserve, so farming free bots cannot mint exchangeable items; excess house chips expire. `/poker bots remove` removes them between hands without paying their house stack to anyone. Bots and hands survive a process restart. Consultants can use both the GUI and commands without bypassing world protections.

Use `/poker values` for the authoritative in-game list. Common bulk items such as cobblestone and dirt are deliberately worth zero. Accepted per-item values are:

| Value | Items |
| ---: | --- |
| 1000 | nether star |
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

### Void Door

Craft two linked dark oak doors with planks and one ender pearl:

```text
wood  wood  empty
wood  pearl empty
wood  wood  empty
```

Each crafted pair receives a unique UUID before the result can be taken, including shift-click crafting. Marked door stacks are limited to two; different pairs never merge. Place both doors, open either one, and cross its black plane to teleport across dimensions. The linked doors synchronize their open/closed state. Closed doors do not teleport. Each placed door keeps only its own chunk loaded using a Forge ticket, removed when the door is mined. The destination searches for a clear, supported side while preserving entry position, facing, and horizontal motion; clear air is accepted if no supported landing exists. Walking forward exits facing away from the door; walking backward exits facing toward it. If both sides are obstructed, teleporting is refused.

The link is saved with the world. Mining either half produces one marked dark oak door with the same pair ID; replacing it restores its endpoint. Shift-breaking either complete endpoint removes both and returns the marked two-door stack, including its code. Saved oak Void Doors migrate to dark oak when their chunks load. Ordinary doors remain unchanged. The pure black plane fits inside dark-oak display frame segments and expands only where a neighboring block fully covers a frame segment; all remain inside the closed leaf, so opening has no spawn delay. Vanilla block displays cannot render the End gateway block entity texture. Touching the plane with any part of a player or dropped item's hitbox teleports it. There are no particles or teleport cooldown. No client resource pack is needed.

To add an optional code, put the newly crafted two-door stack in an anvil and rename it to the code. The output retains both linked doors but stores only a pair-specific SHA-256 digest. Leave the ordinary name to keep the pair unlocked. Right-clicking a coded closed door opens a vanilla anvil text prompt; type the code and take the paper-shaped submit control to open both doors. The paper is never granted, even on shift-click. A successful code entry lets that player reopen the pair without retyping for 15 seconds. Closing either door closes its partner. Unauthorised redstone opening is closed again on the next server tick. Mining preserves the code digest on the dropped door.

### Void Trapdoor

Craft two linked dark-oak Void Trapdoors with an empty top row, then plank + ender pearl + plank, then three planks. They stack up to two within one pair, and different pairs do not merge. Opening either trapdoor opens its partner, even across dimensions. Touching the black plane in the open trapdoor with any part of a player or dropped item's hitbox pops it into clear space above the linked trapdoor, with a gentle push away from the trapdoor's support side. Its narrow black plane and exposed dark-oak frame segments are already present while closed; only edges fully covered by neighboring blocks lose their frame and gain an extended plane. Each placed trapdoor keeps its own chunk loaded; mining preserves its pair ID and releases the ticket. Shift-breaking either complete endpoint removes both and returns the linked two-trapdoor stack.

### Chat Book

Craft a Chat Book from a book and quill plus an amethyst shard. Write one message or `/command` per line and close the editing screen. Sneak-right-click the book to send its lines in order. Chat is broadcast under the player's name as an unsigned server-originated player message; commands execute with that player's own permissions. The book allows up to 32 nonempty lines of 256 characters each per activation. Ordinary books are unaffected.

### Scheduled restarts

Operators and RCON can use `/restartin 5m`, `/restartin 1h30m`, `/restartin status`, and `/restartin cancel`. Durations range from 1 second to 30 days. Players receive localized titles and chat notices immediately, then at applicable milestones (including 5m, 1m, 30s, 10s, and 5s). Countdown timing uses elapsed wall time, so low TPS does not stretch it. Joining players see the current countdown.

At zero, the server writes its timing marker, saves and stops cleanly. **The hosting supervisor must automatically relaunch the server after `stop`** (as on the existing deployment); this mod does not spawn a second JVM. The next fully started Forge server records downtime in `world/data/psychiatryk_restart_history.json`. The ETA uses the median of the last five valid restarts. No ETA is invented before the first measured restart. Offline intervals over 24 hours are excluded.

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
