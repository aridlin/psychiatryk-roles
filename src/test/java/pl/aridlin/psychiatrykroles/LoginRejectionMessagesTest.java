package pl.aridlin.psychiatrykroles;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginRejectionMessagesTest {
    @Test
    void vanillaForgeRequirementPointsToPublicSetupPageInBothLanguages() {
        String rewritten = LoginRejectionMessages.rewriteForgeLoginMessage(
            "This server has mods that require Forge to be installed on the client. Contact your server admin for more details."
        );

        assertTrue(rewritten.contains("Potrzebujesz Forge 1.20.1"));
        assertTrue(rewritten.contains("Forge 1.20.1 and the server modpack are required"));
        assertEquals(2, rewritten.split("info\\.goplanska\\.pl", -1).length - 1);
    }

    @Test
    void incompatibleForgeNetworkVersionPointsToPublicSetupPage() {
        String rewritten = LoginRejectionMessages.rewriteForgeLoginMessage(
            "This modded server is not impl compatible with your modded client. Got net version 3 this server is net version 4"
        );

        assertTrue(rewritten.contains("Niekompatybilna wersja Forge"));
        assertTrue(rewritten.contains("Incompatible Forge version"));
        assertTrue(rewritten.contains("info.goplanska.pl"));
    }

    @Test
    void unrelatedMessagesRemainUntouched() {
        String original = "Server is still starting";
        assertEquals(original, LoginRejectionMessages.rewriteForgeLoginMessage(original));
    }
}
