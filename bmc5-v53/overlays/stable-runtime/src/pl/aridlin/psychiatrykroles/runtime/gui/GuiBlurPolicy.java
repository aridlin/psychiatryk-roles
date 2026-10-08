package pl.aridlin.psychiatrykroles.runtime.gui;

import java.util.Set;

/** Only screens shipped by this addon opt out of Minecraft's world blur. */
public final class GuiBlurPolicy {
    private static final Set<String> OWN_SCREENS = Set.of(
        "pl.aridlin.kukirin.MusicCategoryScreen",
        "pl.aridlin.kukirin.ScooterAdminScreen",
        "pl.aridlin.kukirin.ScooterDismantlerClient$1",
        "pl.aridlin.kukirin.ScooterGuideScreen",
        "pl.aridlin.kukirin.ScooterMusicScreen",
        "pl.aridlin.kukirin.ScooterSettingsScreen",
        "pl.aridlin.kukirin.YouTubeMusicScreen",
        "pl.aridlin.psychiatrykroles.PokerScreen",
        "pl.aridlin.psychiatrykroles.RestartVoteScreen",
        "pl.aridlin.psychiatrykroles.peeb.client.PeebSettingsScreen",
        "pl.aridlin.psychiatrykroles.runtime.client.RuntimeScreen"
    );

    private GuiBlurPolicy() {}

    public static boolean avoidsWorldBlur(String screenClassName, boolean inWorld) {
        return inWorld && OWN_SCREENS.contains(screenClassName);
    }
}
