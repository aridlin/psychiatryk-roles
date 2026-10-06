# Goplanska: Better MC5 v53 + Psychiatryk Roles 3.0.0

Minecraft 1.21.1 · NeoForge 21.1.250 · Java 21.

## Install in Prism or CurseForge

1. Download/import the **official unmodified Better MC [NEOFORGE] BMC5 v53**:
   https://www.curseforge.com/minecraft/modpacks/better-mc-neoforge-bmc5/files/8835241
   In Prism use Add instance → CurseForge → this pack → v53. Keep a backup of an existing instance; make a new instance for this release.
2. Close Minecraft. Open the new instance's Minecraft folder. Copy the JAR
   from this ZIP's `mods` directory into that instance's `mods` folder.
   This ZIP is an addon overlay, not a complete importable CurseForge modpack.
3. Install AutoModpack **4.0.6 for NeoForge/Minecraft 1.21.1**, from its official
   project: https://modrinth.com/mod/automodpack . It is not bundled in this
   own-addon archive. Launch and join `goplanska.pl`; follow AutoModpack's
   update/relaunch prompts to obtain the server-selected remaining additions.
   If an update/fingerprint prompt differs from the server administrator's
   published instructions, ask the administrator before accepting a new identity.
4. Use the same instance for subsequent launches. Do not install the separate
   Portable Scooters/Portable Chams JARs alongside this merged addon: their IDs
   are already included. Do not mix the old S23 v33/v34 modpack into BMC5.

The official Better MC archives and third-party mods are distributed by their
original projects. This archive contains only our tested addon and these docs;
no Better MC archive, foreign mod JAR, world, player data or private music library.
Immersive Portals is not part of this release; Void Doors use classic teleport.

## Recipes and play

Six custom survival crafts remain: Returning Mirror, Loot Lens, Dragon Egg
Shards (one egg → nine), owner-bound KuKirin scooter, Void Door and Void Trapdoor.
See recipes.json for exact patterns/ingredients/components and the website for
readable 3×3 grids. Other legacy custom crafts and ten MoreVillagers workstation
crafts/use/place/pickup are disabled in survival; creative/admin bypass remains.
The separate portable scooter retains its own original recipe, not this BMC
endgame recipe. BMC scooter: `IDI /  E  / IRI`, I iron ingot, D dragon egg shard,
E elytra, R redstone block. Crafting binds it to its owner.

`/poker gui` → emerald bank action: deposit items into shared server stock for
EMC-valued credits, withdraw only actual deposited stock. Click one, Shift-click
stack. Original item components are retained; unsupported EMC items are refused.

## Evidence and license

The included addon SHA-256 is 43868feae389494896b258787cd5305fce43d9d22e595a59b587147f7ff13b53.
The isolated full BMC client passed 27 real checks, including joining, synced
recipes and a five-diamond deposit/withdraw GUI transaction. Dedicated server
checks cover normal doors/trapdoors and escrow. These are not remote AutoModpack
or performance benchmark results. No production activation is asserted by this
archive alone. Code/component license notices remain inside the JAR. The public
model is authored; bundled instrumentals are original CC0, not imported songs.
Source: https://github.com/aridlin/psychiatryk-roles
