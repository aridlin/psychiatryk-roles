# Roles 3.0.6 — player guide

## Roles 3.0.6 — Peeb i muzyka / Peeb and music

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

## Peeb i muzyka — Roles 3.0.6

- **Peeb:** połącz nić, skórę i patyk. Trzymaj przedmiot, aby się przemienić; zwykły pancerz może pozostać założony. Shift+PPM zakłada Peeb w slot klatki piersiowej i wymaga pustego hełmu, nogawek i butów. Peeb daje siedem punktów pancerza i chroni przed upadkiem.
- **Hak i kamera:** przytrzymaj LPM, gdy celownik wskazuje osiągalną powierzchnię; puszczenie zwalnia hak. Sprężyste ciągnięcie liny zachowuje pęd przy zaczepieniu i puszczeniu haka, pozwalając się bujać. Przytrzymaj **lewy Alt**, aby swobodnie rozglądać się podczas przemiany w Peeb; klawisz działa również przy innym przypisaniu w Better MC. E otwiera ekwipunek. `/peebcamera 2..10` zmienia odległość kamery.
- **Muzyka:** odzyskiwanie odtwarzania, ulubione, automatyczny następny utwór, shuffle i wybór kategorii dźwięku. Metadane odświeżają powiadomienie i HUD; `/musichud` zmienia ich ustawienia. Okładka utworu jest widoczna na obracającej się płycie jukeboxa i na jego modelu na plecach.
- **Streaming z ProLianta:** odtwarzanie rozpoczyna się przed pobraniem całego pliku. Usunięto stałe limity długości i wielkości utworu oraz limit czterech źródeł klienta dla streamingu i muzyki Minecraft.
- **Muzyka Minecraft:** lista zawiera 60 utworów ścieżki dźwiękowej, w tym Aria Math, oraz wszystkie 19 płyt z Minecraft 1.21.1. Wyszukaj `Minecraft -` lub `Minecraft Disc -`. Odtwarzanie korzysta z zasobów zainstalowanego Minecrafta.
- **Import:** ekran YouTube/YT Music przyjmuje obsługiwane publiczne adresy utworów i playlist; wynik importu trafia do wspólnej listy serwera. Playlisty zachowują kolejność źródła.
- **Hulajnoga:** zużycie baterii zależy od gazu; hulajnoga w ekwipunku właściciela może odzyskiwać ładunek także podczas jego nieobecności. Pozostają szczegółowy model i odbieranie hulajnogi jako przedmiotu.
- **Kolory i ulepszenia:** ponownie można barwić i ulepszać hulajnogę w stole kowalskim. Przy barwieniu zostaw pierwszy slot (szablon) pusty, włóż hulajnogę jako bazę, a barwnik jako dodatek. Barwienie zachowuje właściciela i istniejące ulepszenia.
- **Elytra i Loyalty:** hulajnoga wytworzona według dotychczasowej receptury z elytrą i odłamkiem jaja smoka dostaje Loyalty I. Nadal wiąże się z właścicielem; ulepszenia zachowują zaklęcie. `/scooterrecall` przywołuje wolną hulajnogę właściciela.

## Peeb and music — Roles 3.0.6

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

## Szafa grająca na plecach — Roles 3.0.6

- Załóż zwykły jukebox do istniejącego slotu **back / plecy**. Zajmuje ten sam slot co plecak i jest widoczny na plecach.
- Otwórz ekwipunek → **Muzyka z pleców**, albo użyj `/jukebox`. Wybierz utwór z tej samej listy, z której korzysta hulajnoga. Dostępne są Loop i Stop.
- `/jukebox stop` zatrzymuje muzykę. `/jukebox loop true|false` zmienia powtarzanie.
- Muzykę słyszą pobliscy gracze; pozycja źródła podąża za graczem. Sound Physics aktualizuje tłumienie ścian i pogłos podczas ruchu.
- Token przedmiotu jukeboxa zachowuje sesję i pozycję utworu przez śmierć, zmianę wymiaru i ponowne założenie. Dźwięk podąża za wyrzuconym przedmiotem, gdy jest on załadowany. Wylogowanie lub umieszczenie przedmiotu w niezaładowanym pojemniku wycisza źródło, aż przedmiot wróci do załadowanego źródła odtwarzania; nie wymusza to ładowania świata ani obecności gracza offline.
- Veinminer działa tylko na połączonych rudach, także bez zaklęcia. Zwykłe bloki i budowle nie są masowo kopane; ścinanie drzew nadal obsługuje FallingTree.

## Wearable jukebox — Roles 3.0.6

- Equip a vanilla jukebox in the existing **back** slot. It occupies the backpack slot and is visible on your back.
- Open the inventory → **Jukebox music**, or use `/jukebox`. Choose from the scooter's existing song library. Loop and Stop are available.
- `/jukebox stop` stops playback; `/jukebox loop true|false` controls looping.
- Nearby players hear the moving sound source. Sound Physics updates wall muffling and reverb as you move.
- The jukebox item token preserves its session and playhead through death, dimension changes and re-equipping. A dropped item remains the moving sound source while loaded. Logout or an unloaded container makes the source inaudible until the item returns to a loaded playback source; chunks and offline players are not force-loaded.
- Veinminer now targets connected ore veins only, including without an enchantment. Ordinary blocks and builds are excluded; FallingTree still handles trees.

## Poprawki 3.0.2 — Polski

- **Hulajnoga:** wraca nasz szczegółowy model i jego tekstury. Shift+PPM podnosi hulajnogę jako przedmiot z zachowanymi ulepszeniami i zawartością; Carry On nie przejmuje hulajnóg.
- **Vein Mining:** działa bez zaklęcia. Kucnij i kop blok narzędziem; limit podstawowy to 50 bloków. Koszt wytrzymałości i głodu pozostaje włączony. Ustawienie aktywacji zmienisz w konfiguracji Vein Mining.

## Fixes 3.0.2 — English

- **Scooter:** the original detailed model and textures are restored. Shift+right-click picks it up as its item, keeping upgrades and stored contents; Carry On excludes scooters.
- **Vein Mining:** works without an enchantment. Crouch and mine with a tool; the base limit is 50 blocks. Durability and hunger costs remain enabled. Activation is configurable in Vein Mining settings.



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
