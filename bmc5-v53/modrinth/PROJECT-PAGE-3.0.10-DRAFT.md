# Psychiatryk on Modrinth — draft page updated, no version published

Project: https://modrinth.com/project/psychiatryk-aridlin

Observed 9 October 2026: the existing project is a draft, visible only to its
members pending moderation. The short summary and English/Polish description
were saved in its editor; both showed a success notice. The description
was updated after the server cutover to identify the live 3.0.10 server,
NeoForge 21.1.252, the public client installers and the remaining in-game
client check. The source link points to the exact
[`feature/stable-client-runtime` branch](https://github.com/aridlin/psychiatryk-roles/tree/feature/stable-client-runtime),
and the links editor confirmed that update. The custom
`Mixed-license` entry now links to [the code/asset rights split](LICENSE-SPLIT.md),
and Modrinth confirmed **License updated**. There are still no uploaded
versions or gallery images. The checklist still requires a version; content
disclosures cannot be selected until a version is uploaded. This truthful
license label does not establish redistribution permission for Peeb assets.
Do not create a second project.

The release copy below is for the *combined* NeoForge 1.21.1 addon. Version 3.0.10
is running on the Goplanska server; a real 3.0.10 client join, visuals and
reconnect remain unverified. Upload a 3.0.10 binary and version changelog
only after the exact dependencies, in-game screenshots and client checks
have passed release checks and the bundled media rights are resolved.
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

The Goplanska server now runs version 3.0.10. This version establishes a
matching client and server runtime. After this one-time client update, the
server can distribute bounded menus, HUD layouts, visual assets, item and
block variants, recipes and selected behaviour changes
without replacing the client JAR. Changed assets are checked against a local
hash cache on join and prepared before use. Some server data and scripts can
also change while players remain connected. New native Minecraft/NeoForge
registrations, packet formats or renderer code still require a client update.
The 3.0.10 client join and rendering checks are still pending, so this
description is not a claim of verified in-game behavior.

## Install and compatibility

Use **Minecraft 1.21.1, Java 21 and NeoForge 21.1.252** for the current
Goplanska release. The [Prism Launcher installer](https://info.goplanska.pl/goplanska-bmc5-v53-installer-3.0.10.mrpack)
and [CurseForge installer](https://info.goplanska.pl/goplanska-bmc5-v53-curseforge-3.0.10.zip)
include the matching Psychiatryk addon and use AutoModpack to download the
remaining server pack on first connection; they are not complete offline
copies of Better MC. A [manual addon ZIP](https://info.goplanska.pl/goplanska-bmc5-v53-roles-3.0.10-addon.zip)
is also available for an existing compatible pack. Follow the
[Goplanska installation page](https://info.goplanska.pl/) for current steps;
the first AutoModpack bootstrap may require a normal game restart. Accessories is required for
wearable features and is supplied separately by the server pack. Do not mix
the combined addon with older standalone modules that register the same IDs.

Source for this release and issues:
https://github.com/aridlin/psychiatryk-roles/tree/feature/stable-client-runtime

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

Serwer Goplanska działa już na wersji **3.0.10**, która wprowadza wspólną
podstawę klienta i serwera. Po tej jednorazowej aktualizacji klienta serwer
może zmieniać ograniczone menu,
układ HUD-u, zasoby graficzne, warianty przedmiotów i bloków, receptury oraz
wybrane reguły bez wymiany pliku moda na komputerach graczy. Klient sprawdza
lokalny cache po dołączeniu i pobiera tylko brakujące zasoby. Część zmian
działa także bez ponownego dołączenia. Nowe typy natywnych bloków, pakietów
sieciowych lub rendererów nadal wymagają aktualizacji klienta.
Dołączenie klienta 3.0.10 i wygląd w grze wymagają jeszcze końcowej kontroli.

Wymagane są **Minecraft 1.21.1, Java 21 i NeoForge 21.1.252**.
Instalatory [Prism Launcher](https://info.goplanska.pl/goplanska-bmc5-v53-installer-3.0.10.mrpack)
i [CurseForge](https://info.goplanska.pl/goplanska-bmc5-v53-curseforge-3.0.10.zip)
zawierają zgodne wydanie Psychiatryk, a AutoModpack pobiera pozostałą część
paczki serwera przy pierwszym połączeniu. Nie są to pełne paczki offline.
Na [stronie Goplanska](https://info.goplanska.pl/) jest aktualna instrukcja;
pierwsze przygotowanie AutoModpack może wymagać zwykłego restartu gry.
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

1. The Goplanska server rollout is live on NeoForge 21.1.252, but do not use
   this as an uploaded Modrinth version announcement until the exact final
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
