#!/usr/bin/env python3
"""Stage, but never publish, the Roles 3.0.10 homepage from the live timeline source."""

from pathlib import Path


ROOT = Path(__file__).resolve().parents[4]
SOURCE = (
    ROOT
    / "work/psychiatryk-workstations-20261008/bmc5-v53/overlays/"
    "item-unlocks/website/index.html"
)
OUTPUT = Path(__file__).with_name("index-3.0.10-prepared.html")
CANONICAL = Path(__file__).with_name("index.html")


def one(page: str, old: str, new: str) -> str:
    count = page.count(old)
    if count != 1:
        raise ValueError(f"Expected one occurrence of {old!r}, got {count}")
    return page.replace(old, new)


def main() -> None:
    original = SOURCE.read_text(encoding="utf-8")
    if original.count("item-unlocks.json") != 1 or original.count("unlock-timeline") < 3:
        raise ValueError("Live unlock timeline source changed; review before staging")
    page = original.replace("21.1.250", "21.1.252")
    if original.count("21.1.250") != 7 or "21.1.250" in page:
        raise ValueError("Unexpected NeoForge loader references")
    page = one(page, "GOPLANSKA / ROLES 3.0.8", "GOPLANSKA / ROLES 3.0.10")
    page = one(
        page,
        'href="goplanska-bmc5-v53-installer.mrpack"',
        'href="goplanska-bmc5-v53-installer-3.0.10.mrpack"',
    )
    page = one(
        page,
        'href="goplanska-bmc5-v53-curseforge.zip"',
        'href="goplanska-bmc5-v53-curseforge-3.0.10.zip"',
    )
    page = one(
        page,
        '<span data-pl="ZIP CURSEFORGE ↓" data-en="CURSEFORGE ZIP ↓">ZIP CURSEFORGE ↓</span>',
        '<span data-pl="INSTALATOR CURSEFORGE ↓" data-en="CURSEFORGE INSTALLER ↓">INSTALATOR CURSEFORGE ↓</span>',
    )
    if page.count('właściwy zestaw serwera pobierze przy pierwszym połączeniu') != 2:
        raise ValueError('Installer description changed')
    page = page.replace(
        'właściwy zestaw serwera pobierze przy pierwszym połączeniu',
        'mody serwera pobiorą się przy pierwszym połączeniu',
    )
    page = one(
        page,
        'the full server pack downloads on first connection',
        'server mods download on first connection',
    )
    page = one(
        page,
        '<p data-pl="Instalator pobiera pełną paczkę po połączeniu; wymaga internetu. Przy następnych aktualizacjach używaj tej samej instancji." data-en="The installer downloads the full pack on connection and needs internet. Keep using this same instance for later updates.">Instalator pobiera pełną paczkę po połączeniu; wymaga internetu. Przy następnych aktualizacjach używaj tej samej instancji.</p>',
        '<p data-pl="To instalatory AutoModpack: po połączeniu pobierają mody serwera i wymagają internetu. Nie są pełną paczką offline. Przy następnych aktualizacjach używaj tej samej instancji." data-en="These are AutoModpack launchers: they download server mods after connection and require internet. They are not a complete offline pack. Keep using the same instance for later updates.">To instalatory AutoModpack: po połączeniu pobierają mody serwera i wymagają internetu. Nie są pełną paczką offline. Przy następnych aktualizacjach używaj tej samej instancji.</p>',
    )
    page = one(
        page,
        "goplanska-bmc5-v53-roles-3.0.8-addon.zip",
        "goplanska-bmc5-v53-roles-3.0.10-addon.zip",
    )
    page = one(
        page,
        "GOPLANSKA · 2026-10-08 · BMC5 v53 / Roles 3.0.8",
        "GOPLANSKA · 2026-10-09 · BMC5 v53 / Roles 3.0.10",
    )
    section = (
        '<section class="chapter" id="roles-3-0-10"><h2 data-pl="ROLES 3.0.10 · AKTUALIZACJE" '
        'data-en="ROLES 3.0.10 · UPDATES">ROLES 3.0.10 · AKTUALIZACJE</h2>'
        '<div class="body-copy"><p data-pl="Pierwsza instalacja 3.0.10 pobiera nową część kliencką '
        'przez AutoModpack i wymaga jednego ponownego uruchomienia gry. Po połączeniu serwer '
        'przesyła wersjonowane ustawienia, widoki i zasoby; kolejne zmiany tych elementów mogą '
        'pojawić się bez ponownego uruchamiania klienta." '
        'data-en="The first 3.0.10 install downloads the new client component through AutoModpack '
        'and requires one game relaunch. On connection, the server sends versioned settings, '
        'views and assets; later changes to those elements can appear without restarting the client.">'
        'Pierwsza instalacja 3.0.10 pobiera nową część kliencką przez AutoModpack i wymaga jednego '
        'ponownego uruchomienia gry. Po połączeniu serwer przesyła wersjonowane ustawienia, '
        'widoki i zasoby; kolejne zmiany tych elementów mogą pojawić się bez ponownego uruchamiania '
        'klienta.</p><p data-pl="Zmiany wymagające nowych natywnych przedmiotów, bloków lub kodu '
        'renderowania nadal będą wymagały aktualizacji klienta. Ekran dołączania pokazuje etapy '
        'pobierania i przygotowania zasobów." '
        'data-en="Changes requiring new native items, blocks or rendering code still require a '
        'client update. The join screen shows download and asset preparation stages.">'
        'Zmiany wymagające nowych natywnych przedmiotów, bloków lub kodu renderowania nadal będą '
        'wymagały aktualizacji klienta. Ekran dołączania pokazuje etapy pobierania i przygotowania '
        'zasobów.</p></div></section>'
    )
    page = one(page, '<section class="chapter" id="paczka">', section + '<section class="chapter" id="paczka">')
    if page.count("item-unlocks.json") != 1 or page.count("unlock-timeline") != original.count("unlock-timeline"):
        raise ValueError("Unlock timeline changed while staging")
    OUTPUT.write_text(page, encoding="utf-8")
    CANONICAL.write_text(page, encoding="utf-8")
    print(f"Staged {OUTPUT} and {CANONICAL} ({len(page.encode('utf-8'))} bytes)")


if __name__ == "__main__":
    main()
