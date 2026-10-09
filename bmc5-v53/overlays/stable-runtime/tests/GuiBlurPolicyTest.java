import pl.aridlin.psychiatrykroles.runtime.gui.GuiBlurPolicy;

public final class GuiBlurPolicyTest {
    private static int checks;

    private static void check(boolean condition, String description) {
        checks++;
        if (!condition) throw new AssertionError(description);
    }

    public static void main(String[] args) {
        for (String name : new String[] {
            "pl.aridlin.kukirin.ScooterMusicScreen",
            "pl.aridlin.kukirin.ScooterDismantlerClient$1",
            "pl.aridlin.psychiatrykroles.PokerScreen",
            "pl.aridlin.psychiatrykroles.runtime.client.RuntimeScreen"
        }) {
            check(GuiBlurPolicy.avoidsWorldBlur(name, true), "owned in-world screen: " + name);
            check(!GuiBlurPolicy.avoidsWorldBlur(name, false), "main-menu rendering unchanged: " + name);
        }
        for (String name : new String[] {
            "net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen",
            "net.minecraft.client.gui.screens.ConfirmScreen",
            "net.minecraft.client.gui.screens.inventory.InventoryScreen",
            "pl.aridlin.kukirin.ScooterMusicScreenEvil",
            "third.party.pl.aridlin.kukirin.ScooterMusicScreen"
        }) {
            check(!GuiBlurPolicy.avoidsWorldBlur(name, true), "unrelated screen unchanged: " + name);
        }
        System.out.println("GuiBlurPolicyTest PASS " + checks + " checks");
    }
}
