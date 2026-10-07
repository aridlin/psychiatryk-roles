package pl.aridlin.psychiatrykroles.jukebox;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import pl.aridlin.kukirin.ScooterMenus;

/** A music shortcut next to the normal inventory; the existing Accessories screen equips it. */
@EventBusSubscriber(modid = "psychiatryk_roles", value = Dist.CLIENT)
public final class WearableJukeboxInventoryButtons {
    private static final java.util.Map<InventoryScreen, Button> BUTTONS = new java.util.WeakHashMap<>();

    @SubscribeEvent
    public static void init(ScreenEvent.Init.Post event) {
        var mc = Minecraft.getInstance();
        if (!(event.getScreen() instanceof InventoryScreen screen) || mc.player == null
                || WearableJukebox.equipped(mc.player).isEmpty()) return;
        var button = Button.builder(Component.translatable("psychiatryk.jukebox.music"),
                ignored -> ScooterMenus.requestWearableMusic(() -> mc.setScreen(new InventoryScreen(mc.player))))
                .bounds(0, 0, 114, 22)
                .tooltip(Tooltip.create(Component.translatable("psychiatryk.jukebox.music_tip")))
                .build();
        BUTTONS.put(screen, button);
        place(screen, button);
        event.addListener(button);
    }

    private static void place(InventoryScreen screen, Button button) {
        // Use the right margin so the vanilla recipe book and scooter's left controls stay usable.
        int margin = screen.width - (screen.getGuiLeft() + 176) - 8;
        boolean beside = margin >= 114;
        button.setWidth(beside ? 114 : Math.min(152, Math.max(80, screen.width - 12)));
        button.setX(beside ? screen.getGuiLeft() + 184 : (screen.width - button.getWidth()) / 2);
        button.setY(beside ? screen.getGuiTop() + 62 : Math.min(screen.height - 24, screen.getGuiTop() + 196));
    }

    @SubscribeEvent
    public static void render(ScreenEvent.Render.Pre event) {
        if (!(event.getScreen() instanceof InventoryScreen screen)) return;
        var button = BUTTONS.get(screen);
        if (button == null) return;
        var player = Minecraft.getInstance().player;
        button.active = player != null && !WearableJukebox.equipped(player).isEmpty();
        place(screen, button);
    }

    @SubscribeEvent
    public static void tooltip(net.neoforged.neoforge.event.entity.player.ItemTooltipEvent event) {
        if (event.getItemStack().is(net.minecraft.world.item.Items.JUKEBOX)) {
            event.getToolTip().add(Component.translatable("psychiatryk.jukebox.equip_tip")
                    .withStyle(net.minecraft.ChatFormatting.GRAY));
        }
    }

    private WearableJukeboxInventoryButtons() {}
}
