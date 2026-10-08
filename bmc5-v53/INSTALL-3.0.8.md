# Goplanska: Better MC5 v53 + Psychiatryk Roles 3.0.8

## Czysta instalacja / stare osobne mody

Importuj instalator jako **nową instancję** w launcherze. Nie nakładaj go na starą paczkę S23.

Jeśli używasz starej instancji i gra zgłasza duplikaty `goplanska_kukirin` lub `goplanska_party_markers`, zamknij grę. Z jej głównego folderu `minecraft/mods` przenieś **tylko** stare `goplanska-kukirin-1.0.0.jar` i plik zaczynający się od `goplanska-party-markers-2.0.` do folderu kopii obok `minecraft`, poza folderami ładowanych modów. Zachowaj AutoModpack i scalony `psychiatryk_roles-3.0.0-bmc5.jar` — jego nazwa pliku jest celowo starsza niż wersja wewnętrzna. Nie usuwaj wszystkich JAR-ów.

### Clean installation / old separate mods

Import the installer as a **new launcher instance**. Do not overlay it onto the old S23 pack.

If a reused instance reports duplicate `goplanska_kukirin` or `goplanska_party_markers`, close the game. Move **only** the old `goplanska-kukirin-1.0.0.jar` and the file beginning `goplanska-party-markers-2.0.` from its base `minecraft/mods` folder into a backup folder beside `minecraft`, outside every loaded mod folder. Keep AutoModpack and the merged `psychiatryk_roles-3.0.0-bmc5.jar`; its filename intentionally predates its internal version. Do not delete every JAR.

## Poprawka uruchamiania 3.0.8 / 3.0.8 launch fix

- Naprawiono wywołanie dostawcy klas ModLauncher w poprawkach zgodności JEI i Flywheel, które uniemożliwiało uruchomienie klienta.
- Uprawy rosną normalnie o każdej porze roku; pozostałe ustawienia pór roku pozostają bez zmian.
- Corrected the ModLauncher class-provider call in the JEI and Flywheel compatibility fixes that prevented client startup.
- Crops grow normally in every season; other season settings remain unchanged.

This is a corrected build of Roles 3.0.8. AutoModpack and these downloads select the same addon SHA-256. Server common code, packet protocols and recipes match the original 3.0.8 build.

## Roles 3.0.8 — Role, Peeb i muzyka / Roles, Peeb and music

- Powracają role Ordynator, Pacjent, Konsultant i Kontraktor; ograniczenia przedmiotów i receptur Better MC zostają.
- Peeba można farbować zwykłymi barwnikami na stole rzemieślniczym. Poprawiono przecinanie oczu i ciała.
- Opcjonalne pokonywanie przeszkód Peebem do 1,3 bloku działa na ziemi i w powietrzu; administrator włącza je w ustawieniach, domyślnie jest wyłączone.
- Karta muzyki pozostaje ostra. Oba HUD-y utworu są o połowę mniejsze i ustawiają się obok rzeczywistej minimapy Xaero.
- Poprawiono strumieniowanie współdzielonej muzyki i ponawianie po chwilowych błędach; naprawiono przenoszenie receptur JEI do plecaków.
- Rozpoczynanie chwytu blisko maksymalnego zasięgu jest spójne z serwerem; poprawiono błąd czyszczenia zakresu starszego Flywheel.
- Ordynator, Pacjent, Konsultant and Contractor roles are restored; Better MC item and recipe restrictions remain.
- Dye Peeb with ordinary dyes in the crafting grid. Eye/body clipping is corrected.
- Optional 1.3-block Peeb stepping works on the ground and in midair; administrators can enable it in settings, and it defaults off.
- Music menu artwork and text stay sharp. Both song cards are half size and sit beside Xaero's actual minimap bounds.
- Shared music streaming and recovery after transient errors are improved; JEI recipe transfers into backpacks are repaired.
- Grapple initiation near the configured range is consistent with the server; a legacy Flywheel range-clearing defect is repaired.

AutoModpack distributes the matching addon. Existing inventory, world, music selections, recipes and unrelated settings are retained. Build-transfer, seasons and old-world migration commands remain disabled.

## Roles 3.0.8 — Pełne przyciąganie Peeba / Peeb full grapple pull

- Hak Peeba daje geometryczne szarpnięcie kamery i przyciąga aż do punktu zaczepienia, zachowując pęd. Domyślna odległość zatrzymania wynosi 0 bloków; przeszkody nadal blokują ruch.
- Odległość zatrzymania można zmienić w `/scooteradmin` → Peeb albo w pliku `config/psychiatryk-peeb.properties`, pole `stopDistance`. Po aktywacji kodu zmiany ustawienia nie wymagają restartu serwera.
- Peeb: zaczepienie haka w przeciwnika zadaje 2 obrażenia, a trafienie ciałem przy przyciąganiu 6, jak żelazny miecz. Obrażenia respektują drużyny i ustawienia PvP.
- Peeb obraca ciało razem z hulajnogą podczas jazdy; przyciąganie dodaje pęd do jazdy zamiast ograniczać prędkość hulajnogi.
- Menu muzyki i HUD używają czytelnego szarego wyglądu Minecraft; karta utworu nie zasłania minimapy.
- Menu muzyki pokazuje okładkę, tytuł i wykonawcę obecnego utworu oraz przycisk polubienia, czytelne ikony, suwak głośności źródła 0–100% i Pause/Resume, które zatrzymuje i wznawia utwór w tej samej pozycji dla słuchaczy. Znaczniki śmierci Xaero są ukryte tylko w widoku świata; pozostają na mapie.
- Peeb's grapple gives a geometric camera tug and pulls all the way to its anchor while preserving momentum. The default stopping distance is 0 blocks; solid obstacles still block movement.
- Change the stopping distance in `/scooteradmin` → Peeb or the `stopDistance` field in `config/psychiatryk-peeb.properties`. After the code update is activated, setting changes do not require a server restart.

- Peeb deals 2 damage when the grapple attaches to an enemy, and 6 on body contact during the pull, matching an iron sword. Damage respects team and PvP rules.
- Peeb follows the scooter body rotation while mounted; the grapple adds momentum to the ride rather than capping the scooter's speed.
- The music menu and HUD use Minecraft-style grey panels; the song card leaves the minimap clear.
- The music menu shows the current track's art, title, artist and Like button, readable control icons, a shared source volume slider (0–100%) and Pause/Resume, preserving the shared track position. Xaero death markers are hidden only in the world HUD and remain on the map.

Existing scooter recipes, models and the Better MC5 base remain unchanged.
Matching server and client addons are required; AutoModpack distributes the update.

Sprawdzono odizolowane testy ruchu i konfiguracji bez uruchamiania pełnego klienta. Odczucie przyciągania w grze wymaga jeszcze potwierdzenia gracza.
Isolated movement and configuration fixtures were checked without launching a full game client. Grapple feel still needs a player check in game.

## Roles 3.0.8 — Peeb i muzyka / Peeb and music

- Import publicznych utworów YouTube/YT Music nie ma minutowego cooldownu. Postęp pobierania pojawia się w menu; utwór trafia do wspólnej biblioteki, gdy jest gotowy.
- Menu muzyki ma przyciski poprzedniego i następnego utworu. Źródła korzystają ze wspólnego zegara odtwarzania: otwarcie menu nie uruchamia piosenki od początku.
- Hak Peeba używa sprężystego ciągnięcia w stylu Peeb Adventures i zachowuje pęd przy zaczepieniu oraz puszczeniu; głowa może rozglądać się bez obracania całego modelu.
- Public YouTube/YT Music song imports have no one-minute cooldown. Download progress appears in the menu; ready tracks enter the shared library.
- The music menu includes previous and next track buttons. A shared playback clock preserves the current song position when opening the menu.
- Peeb uses elastic grapple pull based on Peeb Adventures, preserving momentum on attachment and release; head look is independent from the body.

All existing scooter recipes, upgrades, models and the Better MC5 base remain unchanged.
The protocol change requires the matching server and client addon. AutoModpack distributes it on joining.

Sprawdzenie aktualizacji obejmuje izolowane testy kodeków, streamingu i ruchu bez uruchamiania pełnego klienta. Odsłuch i odczucie haka w grze wymagają jeszcze sprawdzenia przez gracza.
This update was checked with isolated codec, streaming and movement fixtures without launching a full game client. Listening and grapple feel still need a player check in game.

## Peeb i muzyka — Roles 3.0.8

- **Peeb:** połącz nić, skórę i patyk. Trzymaj przedmiot, aby się przemienić; zwykły pancerz może pozostać założony. Shift+PPM zakłada Peeb w slot klatki piersiowej i wymaga pustego hełmu, nogawek i butów. Peeb daje siedem punktów pancerza i chroni przed upadkiem.
- **Hak i kamera:** przytrzymaj LPM, gdy celownik wskazuje osiągalną powierzchnię; puszczenie zwalnia hak. Sprężyste ciągnięcie liny zachowuje pęd przy zaczepieniu i puszczeniu haka, pozwalając się bujać. Przytrzymaj **lewy Alt**, aby swobodnie rozglądać się podczas przemiany w Peeb; klawisz działa również przy innym przypisaniu w Better MC. E otwiera ekwipunek. `/peebcamera 2..10` zmienia odległość kamery.
- **Muzyka:** odzyskiwanie odtwarzania, ulubione, automatyczny następny utwór, shuffle i wybór kategorii dźwięku. Metadane odświeżają powiadomienie i HUD; `/musichud` zmienia ich ustawienia. Okładka utworu jest widoczna na obracającej się płycie jukeboxa i na jego modelu na plecach.
- **Streaming z ProLianta:** odtwarzanie rozpoczyna się przed pobraniem całego pliku. Usunięto stałe limity długości i wielkości utworu oraz limit czterech źródeł klienta dla streamingu i muzyki Minecraft.
- **Muzyka Minecraft:** lista zawiera 60 utworów ścieżki dźwiękowej, w tym Aria Math, oraz wszystkie 19 płyt z Minecraft 1.21.1. Wyszukaj `Minecraft -` lub `Minecraft Disc -`. Odtwarzanie korzysta z zasobów zainstalowanego Minecrafta.
- **Import:** ekran YouTube/YT Music przyjmuje obsługiwane publiczne adresy utworów i playlist; wynik importu trafia do wspólnej listy serwera. Playlisty zachowują kolejność źródła.
- **Hulajnoga:** zużycie baterii zależy od gazu; hulajnoga w ekwipunku właściciela może odzyskiwać ładunek także podczas jego nieobecności. Pozostają szczegółowy model i odbieranie hulajnogi jako przedmiotu.
- **Kolory i ulepszenia:** ponownie można barwić i ulepszać hulajnogę w stole kowalskim. Przy barwieniu zostaw pierwszy slot (szablon) pusty, włóż hulajnogę jako bazę, a barwnik jako dodatek. Barwienie zachowuje właściciela i istniejące ulepszenia.
- **Elytra i Loyalty:** hulajnoga wytworzona według dotychczasowej receptury z elytrą i odłamkiem jaja smoka dostaje Loyalty I. Nadal wiąże się z właścicielem; ulepszenia zachowują zaklęcie. `/scooterrecall` przywołuje wolną hulajnogę właściciela.

## Peeb and music — Roles 3.0.8

- **Peeb:** combine string, leather and a stick. Hold the item to transform while keeping ordinary armour equipped. Shift+right-click wears Peeb in the chest slot and requires helmet, leggings and boots to be empty. Peeb provides seven armour points and prevents fall damage.
- **Grapple and camera:** hold left-click when the target ring points to a reachable surface; release to let go. Elastic rope pull preserves momentum when attaching and releasing the grapple, allowing you to swing. Hold **Left Alt** to freelook while transformed into Peeb, including when Better MC assigns that key to another action. E opens inventory. `/peebcamera 2..10` adjusts camera distance.
- **Music:** playback recovery, favorites, next-track autoplay, shuffle and sound-category selection. Metadata updates the track notification and HUD; `/musichud` controls their settings. Cover art appears on the spinning jukebox disc and worn jukebox model.
- **ProLiant streaming:** playback starts before the entire file is downloaded. Fixed track-duration and file-size limits, and the four-source client limit for streamed and native Minecraft music, are removed.
- **Minecraft music:** the list includes 60 soundtrack tracks, including Aria Math, and all 19 Minecraft 1.21.1 discs. Search for `Minecraft -` or `Minecraft Disc -`. Playback uses the installed Minecraft resources.
- **Import:** the YouTube/YT Music screen accepts supported public song and playlist URLs; imports enter the shared server list. Playlists retain source order.
- **Scooter:** battery use follows throttle; a scooter in its owner's inventory can regain charge while the owner is offline. The detailed model and item pickup remain available.
- **Colours and upgrades:** scooter repainting and upgrades at the smithing table are restored. For repainting, leave the first template slot empty, put the scooter in the base slot and dye in the addition slot. Repainting keeps the owner and existing upgrades.
- **Elytra and Loyalty:** the existing Elytra and dragon egg shard scooter recipe now gives Loyalty I. Owner binding remains, and upgrades preserve the enchantment. `/scooterrecall` recalls an unoccupied scooter belonging to its owner.

## Poprawka dźwięku — Roles 3.0.4

Postawiony jukebox z utworem z listy serwera (np. Barka) aktualizuje teraz tłumienie ścian i pogłos również wtedy, gdy porusza się słuchacz. Poprawka obejmuje tę samą muzykę na hulajnogach i w jukeboxach na plecach. AutoModpack pobierze ją przy następnym uruchomieniu gry.

## Sound fix — Roles 3.0.4

A placed jukebox playing a server song (such as Barka) now refreshes wall muffling and reverb as the listener moves. Scooter and worn jukebox songs retain the same processing. AutoModpack downloads this patch on the next game launch.

## Szafa grająca na plecach — Roles 3.0.8

- Załóż zwykły jukebox do istniejącego slotu **back / plecy**. Zajmuje ten sam slot co plecak i jest widoczny na plecach.
- Otwórz ekwipunek → **Muzyka z pleców**, albo użyj `/jukebox`. Wybierz utwór z tej samej listy, z której korzysta hulajnoga. Dostępne są Loop i Stop.
- `/jukebox stop` zatrzymuje muzykę. `/jukebox loop true|false` zmienia powtarzanie.
- Muzykę słyszą pobliscy gracze; pozycja źródła podąża za graczem. Sound Physics aktualizuje tłumienie ścian i pogłos podczas ruchu.
- Token przedmiotu jukeboxa zachowuje sesję i pozycję utworu przez śmierć, zmianę wymiaru i ponowne założenie. Dźwięk podąża za wyrzuconym przedmiotem, gdy jest on załadowany. Wylogowanie lub umieszczenie przedmiotu w niezaładowanym pojemniku wycisza źródło, aż przedmiot wróci do załadowanego źródła odtwarzania; nie wymusza to ładowania świata ani obecności gracza offline.
- Veinminer działa tylko na połączonych rudach, także bez zaklęcia. Zwykłe bloki i budowle nie są masowo kopane; ścinanie drzew nadal obsługuje FallingTree.

## Wearable jukebox — Roles 3.0.8

- Equip a vanilla jukebox in the existing **back** slot. It occupies the backpack slot and is visible on your back.
- Open the inventory → **Jukebox music**, or use `/jukebox`. Choose from the scooter's existing song library. Loop and Stop are available.
- `/jukebox stop` stops playback; `/jukebox loop true|false` controls looping.
- Nearby players hear the moving sound source. Sound Physics updates wall muffling and reverb as you move.
- The jukebox item token preserves its session and playhead through death, dimension changes and re-equipping. A dropped item remains the moving sound source while loaded. Logout or an unloaded container makes the source inaudible until the item returns to a loaded playback source; chunks and offline players are not force-loaded.
- Veinminer now targets connected ore veins only, including without an enchantment. Ordinary blocks and builds are excluded; FallingTree still handles trees.

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
   `mods/psychiatryk_roles-3.0.0-bmc5.jar` from this ZIP into that instance's `mods` folder. Replace an earlier merged Roles JAR; do not keep both versions. The server-managed filename is retained; the installed mod version is 3.0.8.
   Also copy `config/carryon-common.toml` for the scooter exclusion, then `config/veinmining-server.toml` from the ZIP into the instance's `config` folder for the ores-only preset.
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
original projects. This archive contains our tested addon, its selected Carry On/ores-only VeinMining configurations, release metadata and these docs;
no Better MC archive, foreign mod JAR, world, player data or private music library.
Immersive Portals is not part of this release; Void Doors use classic teleport.

## Recipes and play

Peeb adds a shapeless string + leather + stick recipe. The six original custom survival crafts remain: Returning Mirror, Loot Lens, Dragon Egg
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

- **Plecaki:** Sophisticated Backpacks 3.25.69.1979 i Sophisticated Core 1.4.70.2131 są dodatkami zestawu serwera. AutoModpack pobiera je przy dołączeniu; nie są dołączone do tego własnego ZIP-a. Pozostają JEI 19.27.0.343 i Distraction Free Recipes.
- **Światy testowe:** `/testworld normal` i `/testworld flat` otwierają dwa światy z jednym wspólnym, osobnym ekwipunkiem testowym. `/testworld back` przywraca ekwipunek zwykłego świata i poprzednią pozycję. Ekwipunek, plecaki, zbroja, druga ręka i skrzynia Endu są rozdzielone między bankiem zwykłym i testowym.
- **Uprawnienia:** pacjenci zaczynają testowanie w Creative, konsultanci w Survival. Pacjent może użyć `/testworld mode survival` lub `/testworld mode creative`; konsultant potrzebuje nadania przez aridlin. Tylko profil aridlin z uprawnieniem operatora może użyć `/testworld grant <gracz-online> creative|survival`. Pomoc operatora: `/psychiatrykhelp admin`.
- **Poker:** na serwerze dedykowanym wpłata i wypłata przedmiotów są zablokowane w obu światach testowych, również w menu i przez Shift+klik. Najpierw wróć przez `/testworld back`. Istniejące żetony portfela/stolików nadal mogą służyć do gry.
- **Muzyka:** otwórz ekwipunek hulajnogi → Music, wyszukaj utwór z listy serwera i wybierz go. Hulajnoga wymaga ulepszenia blokiem dźwiękowym. Shift+PPM na jukeboxie otwiera tę samą listę. Dostępne są Loop i Stop; zwykły PPM zachowuje działanie vanilla. Utwory są pobierane z hostingu HTTPS i buforowane lokalnie; nie trzeba kopiować biblioteki do paczki. `/scootermusic <nazwa.wav>` ma podpowiedzi, a `/scootermusic stop` zatrzymuje hulajnogę.
- **Odłamek jaja:** ma teraz własną teksturę. Receptura nadal daje dziewięć odłamków z jednego smoczego jaja; wszystkie sześć siatek receptur pozostaje bez zmian.

## Roles 3.0.2 — English

- **Backpacks:** Sophisticated Backpacks 3.25.69.1979 and Sophisticated Core 1.4.70.2131 are server-pack additions. AutoModpack obtains them on joining; they are not bundled in this own-addon ZIP. JEI 19.27.0.343 and Distraction Free Recipes are retained.
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

The included addon SHA-256 is 56cc13966c2ed202ff54ac9e0b401fce32325842d93fff2be22b1ee14de9b614.
This release restores the original detailed scooter mesh, UVs, lighting normals and texture variants using cached native Minecraft geometry. The isolated full BMC client checks cover the actual rendering and resource reload, unenchanted vein mining and scooter item pickup with preserved upgrades/storage. Existing unrelated runtime receipts retain their original measured candidate IDs.
No production activation or performance benchmark is asserted by this ZIP alone. Code/component license notices remain inside the JAR. The restored scooter model uses the user-supplied Sketchfab Standard licensed model by kovsh (https://sketchfab.com/3d-models/fddbc46d599240bba8258e6d2c4daa59); the Peeb/game assets retain their original notices and are not relicensed by the code license.
Source: https://github.com/aridlin/psychiatryk-roles
