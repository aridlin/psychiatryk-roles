# Goplanska: Better MC5 v53 + Psychiatryk Roles 3.0.2

Minecraft 1.21.1 · NeoForge 21.1.250 · Java 21.

## Quick first-run installer

Download the Prism .mrpack or CurseForge installer ZIP from https://info.goplanska.pl/.
Import it, launch and join goplanska.pl; AutoModpack downloads the complete current
server pack and requests a relaunch. The tiny installer is not an offline full pack.

## Manual install in Prism or CurseForge

1. Download/import the **official unmodified Better MC [NEOFORGE] BMC5 v53**:
   https://www.curseforge.com/minecraft/modpacks/better-mc-neoforge-bmc5/files/8835241
   In Prism use Add instance → CurseForge → this pack → v53. Then Edit instance → Version → NeoForge → Change version → **21.1.250**. The server requires this exact loader, even if the imported v53 default differs. Keep the old instance for rollback.
2. Close Minecraft. Open the new instance's Minecraft folder. Copy the JAR
   `mods/psychiatryk_roles-3.0.0-bmc5.jar` from this ZIP into that instance's `mods` folder. Replace an earlier merged Roles JAR; do not keep both versions. The server-managed filename is retained; the installed mod version is 3.0.2.
   This ZIP is an addon overlay, not a complete importable CurseForge modpack.
3. Install AutoModpack **4.0.6 for NeoForge/Minecraft 1.21.1**, from its official
   version: https://modrinth.com/mod/automodpack/version/e6HhD1Ik . It is not bundled in this
   own-addon archive. Launch and join `goplanska.pl`; follow AutoModpack's
   update/relaunch prompts to obtain the server-selected remaining additions.
   First-connection fingerprint: e47e202810281086f31e37c74ec121d611b31e50adb6a4a68131fbabe2f0cee6
   If it differs, do not accept a different server identity.
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

## Roles 3.0.2 — Polski

- **Plecaki:** Sophisticated Backpacks 3.25.69.1979 i Sophisticated Core 1.4.70.2131 są dodatkami zestawu serwera. AutoModpack pobiera je przy dołączeniu; nie są dołączone do tego własnego ZIP-a. Pozostaje JEI 19.27.0.343.
- **Światy testowe:** `/testworld normal` i `/testworld flat` otwierają dwa światy z jednym wspólnym, osobnym ekwipunkiem testowym. `/testworld back` przywraca ekwipunek zwykłego świata i poprzednią pozycję. Ekwipunek, plecaki, zbroja, druga ręka i skrzynia Endu są rozdzielone między bankiem zwykłym i testowym.
- **Uprawnienia:** pacjenci zaczynają testowanie w Creative, konsultanci w Survival. Pacjent może użyć `/testworld mode survival` lub `/testworld mode creative`; konsultant potrzebuje nadania przez aridlin. Tylko profil aridlin z uprawnieniem operatora może użyć `/testworld grant <gracz-online> creative|survival`. Pomoc operatora: `/psychiatrykhelp admin`.
- **Poker:** na serwerze dedykowanym wpłata i wypłata przedmiotów są zablokowane w obu światach testowych, również w menu i przez Shift+klik. Najpierw wróć przez `/testworld back`. Istniejące żetony portfela/stolików nadal mogą służyć do gry.
- **Muzyka:** otwórz ekwipunek hulajnogi → Music, wyszukaj utwór z listy serwera i wybierz go. Hulajnoga wymaga ulepszenia blokiem dźwiękowym. Shift+PPM na jukeboxie otwiera tę samą listę. Dostępne są Loop i Stop; zwykły PPM zachowuje działanie vanilla. Utwory są pobierane z hostingu HTTPS i buforowane lokalnie; nie trzeba kopiować biblioteki do paczki. `/scootermusic <nazwa.wav>` ma podpowiedzi, a `/scootermusic stop` zatrzymuje hulajnogę.
- **Odłamek jaja:** ma teraz własną teksturę. Receptura nadal daje dziewięć odłamków z jednego smoczego jaja; wszystkie sześć siatek receptur pozostaje bez zmian.

## Roles 3.0.2 — English

- **Backpacks:** Sophisticated Backpacks 3.25.69.1979 and Sophisticated Core 1.4.70.2131 are server-pack additions. AutoModpack obtains them on joining; they are not bundled in this own-addon ZIP. JEI 19.27.0.343 is retained.
- **Testing worlds:** `/testworld normal` and `/testworld flat` enter two worlds sharing one separate testing inventory. `/testworld back` restores the ordinary-world inventory and previous position. Inventory, backpacks, armor, offhand and ender items are separated between ordinary and testing banks.
- **Permissions:** patients start testing in Creative, consultants in Survival. Patients may use `/testworld mode survival` or `/testworld mode creative`; consultants need a grant from aridlin. Only the aridlin profile with operator permission can use `/testworld grant <online-player> creative|survival`. Operator help: `/psychiatrykhelp admin`.
- **Poker:** the dedicated server blocks item deposits and withdrawals in both testing worlds, including menu and Shift clicks. Use `/testworld back` first. Existing wallet/table chips remain usable for poker.
- **Music:** open the scooter inventory → Music, search the server list and select a track. The scooter needs a note-block upgrade. Shift+right-click a jukebox opens the same list. Loop and Stop are available; ordinary right-click remains vanilla. Tracks download from HTTPS hosting into a local cache, so no music library needs to be copied into the pack. `/scootermusic <name.wav>` has completion; `/scootermusic stop` stops scooter playback.
- **Egg shard:** it now has its own texture. One dragon egg still crafts nine shards, and all six recipe grids remain unchanged.

## Poprawki 3.0.2 — Polski

- **Hulajnoga:** wraca nasz szczegółowy model i jego tekstury. Shift+PPM podnosi hulajnogę jako przedmiot z zachowanymi ulepszeniami i zawartością; Carry On nie przejmuje hulajnóg.
- **Vein Mining:** działa bez zaklęcia. Kucnij i kop blok narzędziem; limit podstawowy to 50 bloków. Koszt wytrzymałości i głodu pozostaje włączony. Ustawienie aktywacji zmienisz w konfiguracji Vein Mining.

## Fixes 3.0.2 — English

- **Scooter:** the original detailed model and textures are restored. Shift+right-click picks it up as its item, keeping upgrades and stored contents; Carry On excludes scooters.
- **Vein Mining:** works without an enchantment. Crouch and mine with a tool; the base limit is 50 blocks. Durability and hunger costs remain enabled. Activation is configurable in Vein Mining settings.

## Evidence and license

The included addon SHA-256 is ea63b4c4fa3287861d2066c3b636d80772d162a01e6a643e92ec55a526329ad6.
This release restores the original detailed scooter mesh, UVs, lighting normals and texture variants using cached native Minecraft geometry. The isolated full BMC client checks cover the actual rendering and resource reload, unenchanted vein mining and scooter item pickup with preserved upgrades/storage. Existing unrelated runtime receipts retain their original measured candidate IDs.
No production activation or performance benchmark is asserted by this ZIP alone. Code/component license notices remain inside the JAR. The restored scooter model uses the user-supplied Sketchfab Standard licensed model by kovsh (https://sketchfab.com/3d-models/fddbc46d599240bba8258e6d2c4daa59); bundled instrumentals are original CC0, not imported songs.
Source: https://github.com/aridlin/psychiatryk-roles
