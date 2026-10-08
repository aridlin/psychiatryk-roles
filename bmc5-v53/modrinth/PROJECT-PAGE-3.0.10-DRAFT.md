# Psychiatryk on Modrinth — draft page updated, no version published

Project: https://modrinth.com/project/psychiatryk-aridlin

Observed 9 October 2026: the existing project is a draft, visible only to its
members pending moderation. The short summary and English/Polish description
below were saved in its editor; both showed a success notice. There are still
no uploaded versions or gallery images, and its license field reads
**Unknown**. Modrinth's publishing checklist still requires a license and a
version; content disclosures cannot be selected until a version is uploaded.
Do not create a second project.

The copy below is for the *combined* NeoForge 1.21.1 addon. The existing
project description presents 3.0.10 as an upcoming foundation. Upload a
3.0.10 binary and version changelog only after the final binary, exact
dependencies, in-game screenshots and client/server rollout have passed
release checks.
The standalone KuKirin/Peeb editions are source-stage work and should not be
listed as downloadable editions on this project until their own files exist.

## Short summary (English)

Peeb grappling, KuKirin scooters, linked Void Doors, music and multiplayer
roles for Minecraft 1.21.1 on NeoForge.

## Project description (English)

# Psychiatryk

Psychiatryk brings a set of character, movement and multiplayer features to
Minecraft 1.21.1. Become Peeb and swing from a grappling hook, ride and
upgrade a KuKirin scooter, travel through paired Void Doors and play music
through jukeboxes and scooter speakers. The combined server addon also
contains roles, parties, poker and administrator tools. Servers decide which
features and recipes are available.

## Play

- **Peeb:** transform, recolor the character with ordinary dyes, grapple onto
  terrain and carry momentum through a swing. Server settings control movement
  and combat behaviour.
- **KuKirin:** ride, steer, drift, jump, upgrade and recall a personal scooter.
  Storage and music depend on installed upgrades and server rules.
- **Void Doors and items:** use linked door and trapdoor pairs for travel,
  including across dimensions, alongside other custom items such as the Mirror
  of Returning. A server may restrict recipes or access.
- **Shared music:** play server-library tracks from compatible jukeboxes and
  scooters. The server may offer public YouTube/YT Music imports; this is not a
  claim of Spotify link playback.
- **Multiplayer:** role controls, parties, poker and help tools are available
  in the combined server edition. Some commands require operator permission.

## Updates beginning with 3.0.10

Version 3.0.10 establishes a matching client and server runtime. After this
one-time client update, the server can distribute bounded menus, HUD layouts,
visual assets, item and block variants, recipes and selected behaviour changes
without replacing the client JAR. Changed assets are checked against a local
hash cache on join and prepared before use. Some server data and scripts can
also change while players remain connected. New native Minecraft/NeoForge
registrations, packet formats or renderer code still require a client update.

## Install and compatibility

Use **Minecraft 1.21.1, Java 21 and the matching NeoForge 21.1.x version**
specified by the release. Install the same Psychiatryk edition on the server
and every client. For the Goplanska Better MC server, follow its exact modpack
instructions; the combined addon is not a copy of Better MC and does not
include the rest of that pack. Accessories is required for the wearable
features and is supplied separately by the server pack. Do not mix this
combined addon with older standalone modules that register the same IDs.

Source and issues: https://github.com/aridlin/psychiatryk-roles

Authored Java code is GPL-3.0-or-later. Some character art and sound are
third-party works with separate rights and attribution; see the notices in
the release. Peeb Adventures original game and 3D art are credited to John
Ellis / FeverdreamJohnny; Peeb's design is credited to Aaron.

## Opis projektu (polski)

# Psychiatryk

Psychiatryk dodaje do Minecrafta 1.21.1 Peeba z hakiem, hulajnogę KuKirin,
połączone Drzwi Pustki, wspólną muzykę oraz funkcje gry wieloosobowej. W
scalonej wersji serwerowej są też role, drużyny, poker i narzędzia
administratora. Dostępność funkcji i receptur zależy od ustawień serwera.

- **Peeb:** przemiana, barwienie zwykłymi barwnikami i bujanie się na haku z
  zachowaniem pędu. Zasady ruchu i walki ustala serwer.
- **KuKirin:** jazda, skręcanie, drift, skoki, ulepszenia, schowek, muzyka i
  przywołanie własnej hulajnogi.
- **Drzwi Pustki:** pary drzwi i klap do podróży, również między wymiarami;
  serwer może ograniczać receptury i dostęp.
- **Muzyka:** współdzielona biblioteka utworów w szafach grających i
  hulajnogach. Nie oznacza to obsługi linków Spotify.

Wersja **3.0.10** wprowadza wspólną podstawę klienta i serwera. Po tej
jednorazowej aktualizacji klienta serwer może zmieniać ograniczone menu,
układ HUD-u, zasoby graficzne, warianty przedmiotów i bloków, receptury oraz
wybrane reguły bez wymiany pliku moda na komputerach graczy. Klient sprawdza
lokalny cache po dołączeniu i pobiera tylko brakujące zasoby. Część zmian
działa także bez ponownego dołączenia. Nowe typy natywnych bloków, pakietów
sieciowych lub rendererów nadal wymagają aktualizacji klienta.

Wymagane są **Minecraft 1.21.1, Java 21 i wersja NeoForge 21.1.x wskazana
przy wydaniu**. Klient i serwer muszą mieć zgodne wydanie Psychiatryk.
Instalację dla serwera Goplanska opisuje osobna instrukcja paczki Better MC.
Kod Java jest na licencji GPL-3.0-or-later; materiały Peeb Adventures mają
odrębne prawa i informacje o autorach w pakiecie wydania.

## Proposed 3.0.10 version changelog — English

- One-time matching client/server runtime foundation for later server-driven
  updates. Older clients must update for the new native variant registrations.
- Bounded, revisioned server definitions for menus, controls, signed variants,
  recipes and behaviour; automatic reload of validated configuration.
- Hash-verified PNG/JSON asset preload and local cache, with changed files
  transferred before use and previous visuals retained if an update fails.
- Optional server-pushed HUD scenes, Peeb animation data, scooter visual rules
  and sparse decorative block/item variants.
- Server-controlled grapple policy and hooks; selected server scripts can be
  reloaded without a client restart. New native registry or renderer code
  still requires a new client build.
- Targeted background-blur change for owned in-world screens, plus a
  join-time asset progress view. In-game visuals require final client QA.

## Proponowany opis wersji 3.0.10 — polski

- Jednorazowa aktualizacja wspólnej podstawy klienta i serwera. Starszy
  klient musi zaktualizować moda z powodu nowych natywnych rejestracji.
- Serwerowe definicje menu, kontrolek, podpisanych wariantów przedmiotów,
  receptur i zachowań, z walidacją i przeładowaniem konfiguracji.
- Zasoby PNG/JSON sprawdzane skrótem SHA-256 i zapisywane w cache; zmiany
  przygotowują się przed użyciem, a błąd nie usuwa poprzednich grafik.
- Opcjonalne sceny HUD-u, dane animacji Peeba, reguły wyglądu hulajnogi i
  warianty dekoracyjnych przedmiotów oraz bloków sterowane przez serwer.
- Ustawienia i zdarzenia haka sterowane przez serwer; wybrane skrypty można
  przeładować bez restartu gry klienta.
- Poprawka rozmycia własnych ekranów i ekran postępu podczas dołączania.
  Wygląd w grze wymaga jeszcze końcowej kontroli klienta.

## Publication gates

1. Do not use this as an uploaded version announcement until the exact final
   JAR passes the full pack/client join, cache, GUI and reconnect checks.
2. Verify redistribution rights for the bundled Peeb art/audio. The source
   license does **not** grant those assets GPL rights. Choose the project
   license/disclosures only after reviewing the actual published artifact.
   The tested 3.0.10 JAR contains Peeb model/animation JSON and OGG sounds.
   The [original creator's page](https://feverdreamjohnny.itch.io/peeb-adventures-hps1-demodisk-edition)
   credits John Ellis for 3D art and Aaron for Peeb's design but publishes no
   asset-redistribution grant on that page. For modpacks,
   [Modrinth's permission guide](https://support.modrinth.com/en/articles/8797527-obtaining-modpack-permissions)
   asks authors to establish a redistribution license, an explicit project
   statement, or author permission for third-party content. We infer that the
   same underlying rights question applies to assets bundled in this JAR.
   Credit alone does not establish
   those rights. Keep the public Modrinth version unpublished until rights
   evidence is recorded or the affected assets are replaced; choosing GPL
   for the authored Java source would not license the bundled artwork.
3. Upload a real version with exact NeoForge/Java/dependency requirements,
   then add the applicable content disclosures, including AI-assisted work if
   required by Modrinth's current interface.
4. Add real in-game gallery images from the matching build; do not use a seed
   candidate, mocked UI, or a client from an older edition as 3.0.10 proof.
