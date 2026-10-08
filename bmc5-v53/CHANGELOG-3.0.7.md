# Roles 3.0.7

## Roles 3.0.7 — Pełne przyciąganie Peeba / Peeb full grapple pull

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

Addon SHA-256: `ff773260893687f62a1c7dfd570a5c374110e972ce84f6fd51952620a7e7f303`
