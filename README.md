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

Players choose their persistent language with:

```text
/polski
/english
```

Either command can be used repeatedly. Every use refreshes the current player's marked item names and lore, updates their role label, and replays the consultant explanation. Returning consultants with an assigned language also receive the explanation after joining.
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

Protected recipes are intended to be crafted by patients or directors and handed to consultants. Consultant crafting attempts are rejected.

- **Consultant Sword:** two sticks above one cobblestone; attacks hostile mobs only.
- **Consultant Pickaxe:** three sticks across the top, then two vertically centered cobblestone; mines stone and cobblestone.
- **Extractor:** five sticks in a plus; removes marked consultant equipment.
- **Importer:** eight sticks in a ring; right-click with an ordinary item in the offhand to mark and move the stack.
- **Passage Staff:** three vertical sticks; right-click or drop it to change between the restricted world and the consultant world.

The **Mirror of Returning** is an ordinary echo shard produced from one glass block over one stick. Consultants may craft it. One use teleports to a valid personal respawn point, falling back to overworld spawn. A second use within three seconds goes directly to overworld spawn.

## Consultant world

`psychiatryk_roles:konsultanci` is a second, independent overworld generated with vanilla overworld noise and biomes. The Passage Staff stores a separate last position on each side. First entry uses the dimension spawn.

Consultants remain in Survival and have unrestricted building, combat, containers, vehicles, item pickup, and ordinary item dropping there. Protected consultant equipment remains controlled.

Dynmap discovers the dimension as `psychiatryk_roles_konsultanci` and generates its surface, cave, and flat map layers.

## Sleep behavior

Consultants in the restricted overworld cannot enter beds and are excluded from the required sleeper count. The original `playersSleepingPercentage` value is retained as the base for eligible non-consultant players.

## Local command generator

[`docs/index.html`](docs/index.html) is a dependency-free command builder and operator reference. It includes action selection, proximity conditions, presets, recipe diagrams, and maintenance notes.

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

Persistent role, code, language, travel-position, audit-log, and sleep-base data is stored in the overworld saved-data file `psychiatryk_roles.dat`.

## Safety notes

- Keep a rollback JAR before each live replacement.
- Do not run inventory-clearing commands as a smoke test on real players.
- The local generator binds to loopback only.
- Server credentials, RCON passwords, SFTP details, and player data do not belong in this repository.
