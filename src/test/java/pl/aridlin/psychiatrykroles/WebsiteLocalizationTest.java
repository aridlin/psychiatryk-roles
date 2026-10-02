package pl.aridlin.psychiatrykroles;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class WebsiteLocalizationTest {
    private static String page() throws IOException {
        return Files.readString(Path.of("website", "index.html"));
    }

    @Test
    void exposesPersistentPolishAndEnglishControls() throws IOException {
        String html = page();
        assertTrue(html.contains("data-language=\"pl\""));
        assertTrue(html.contains("data-language=\"en\""));
        assertTrue(html.contains("localStorage.setItem(languageStorageKey"));
        assertTrue(html.contains("document.documentElement.lang = currentLanguage"));
    }

    @Test
    void automaticLanguageUsesCountryOnlyAndLocalFallbacks() throws IOException {
        String html = page();
        assertTrue(html.contains("?fields=success,country_code"));
        assertTrue(html.contains("credentials:'omit'"));
        assertTrue(html.contains("referrerPolicy:'no-referrer'"));
        assertTrue(html.contains("cache:'no-store'"));
        assertTrue(html.contains("navigator.languages"));
        assertTrue(html.contains("Europe/Warsaw"));
        assertTrue(html.contains("controller.abort()"));
    }

    @Test
    void translationsCoverEveryMajorPageArea() throws IOException {
        String html = page();
        assertTrue(html.contains("'PRZEDMIOTY':'ITEMS'"));
        assertTrue(html.contains("'role i zasady.':'roles and rules.'"));
        assertTrue(html.contains("mody sezonu."));
        assertTrue(html.contains("season mods."));
        assertTrue(html.contains("'narzędzia konsultanta.':'consultant tools.'"));
        assertTrue(html.contains("'generator komend.':'command generator.'"));
        assertTrue(html.contains("'NA GÓRĘ ↑':'BACK TO TOP ↑'"));
    }

    @Test
    void removedFooterCopyIsNotPublished() throws IOException {
        String html = page();
        assertFalse(html.contains("To żartobliwy klimat serwera"));
    }

    @Test
    void pokerHasAVisibleLocalizedViewWalletExchangeAndBots() throws IOException {
        String html = page();
        assertTrue(html.contains("data-view-link=\"poker\""));
        assertTrue(html.contains("data-view=\"poker\""));
        assertTrue(html.contains("'/poker" ) || html.contains("/poker buyin"));
        assertTrue(html.contains("muszle łodzika"));
        assertTrue(html.contains("nautilus shells"));
        assertTrue(html.contains("/poker exchange in"));
        assertTrue(html.contains("/poker bots add"));
        assertTrue(html.contains("persistent chip wallet"));
        assertTrue(html.contains("/poker gui"));
        assertTrue(html.contains("Bots are free and start with 100 house chips"));
        assertTrue(html.contains("['info','items','poker','generator']"));
    }

    @Test
    void chatBookRecipeExplainsTheActivationAndHasEnglishCopy() throws IOException {
        String html = page();
        assertTrue(html.contains("id=\"chat-book-recipe\""));
        assertTrue(html.contains("1 × książka i pióro + 1 × odłamek ametystu"));
        assertTrue(html.contains("1 × book and quill + 1 × amethyst shard"));
        assertTrue(html.contains("Sneak-right-click") || html.contains("sneak and right-click"));
    }

    @Test
    void publishesTheLiveSeasonPackAndRetiresTheMigrationPage() throws IOException {
        String html = page();
        assertTrue(html.contains("NeoForge 21.1.252"));
        assertTrue(html.contains("pl01-waw.icsv.pl:50035"));
        assertTrue(html.contains("-klient.mrpack\" download>"));
        assertFalse(html.contains("href=\"migracja.html\""));
        assertFalse(html.contains("http://pl01-waw.icsv.pl:21007/"));
    }
}
