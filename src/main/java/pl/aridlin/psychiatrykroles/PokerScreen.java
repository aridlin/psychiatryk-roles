package pl.aridlin.psychiatrykroles;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

import java.util.List;

/** A visual skin for the unchanged, server-authoritative 54-slot poker contract. */
@EventBusSubscriber(modid = PsychiatrykRoles.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class PokerScreen extends AbstractContainerScreen<PokerMenu> {
    private static final int BACK = 0xFF171913;
    private static final int BORDER = 0xFFB6924F;
    private static final int FELT = 0xFF184B3F;
    private static final int FELT_INNER = 0xFF216252;
    private static final int TEXT = 0xFFE9DCBE;
    private static final int MUTED = 0xFFAAA591;
    private static final int SIDE_WIDTH = 104;
    private final String[] previousCards = new String[7];
    private final long[] revealAt = new long[7];

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(PokerMenus.TYPE.get(), PokerScreen::new);
    }

    private PokerScreen(PokerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageHeight = 222;
    }

    @Override
    protected void init() {
        // On small GUI scales, keep the same compact slot layout and leave the
        // full log in the book's lore. Wider screens get an always-visible log.
        imageWidth = width >= 288 ? 176 + SIDE_WIDTH : 176;
        super.init();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        boolean exchange = isExchange();
        graphics.fill(x, y, x + imageWidth, y + imageHeight, BORDER);
        graphics.fill(x + 2, y + 2, x + imageWidth - 2, y + imageHeight - 2, BACK);
        graphics.fill(x + 5, y + 16, x + 171, y + 126, 0xFF3B2A19);
        graphics.fill(x + 7, y + 18, x + 169, y + 124, exchange ? 0xFF333C2E : FELT);
        if (!exchange) {
            // Layered edges read as a poker table while preserving the vanilla
            // slot hitboxes. All card, seat and action data still comes from the server.
            graphics.fill(x + 15, y + 28, x + 161, y + 118, FELT_INNER);
            graphics.fill(x + 22, y + 34, x + 154, y + 112, FELT);
            graphics.fill(x + 33, y + 50, x + 143, y + 70, 0x55101915);
            for (int slot = 27; slot <= 34; slot++) {
                Slot button = menu.getSlot(slot);
                graphics.fill(x + button.x - 1, y + button.y - 1,
                    x + button.x + 17, y + button.y + 17, 0xFF8B6F3F);
                graphics.fill(x + button.x, y + button.y,
                    x + button.x + 16, y + button.y + 16, 0xFF213A32);
            }
            paintTurnPulse(graphics, x, y);
            paintCardReveals(graphics, x, y);
        }
        graphics.fill(x + 4, y + 130, x + 172, y + 131, BORDER);
        graphics.fill(x + 5, y + 139, x + 171, y + 218, 0xFF23271D);
        if (imageWidth > 176) paintSidebar(graphics, x, y);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        String titleText = isExchange() ? label("Wymiana", "Exchange") : label("Stół pokerowy", "Poker table");
        graphics.drawString(font, titleText, 8, 6, TEXT, false);
        graphics.drawString(font, playerInventoryTitle, 8, 129, MUTED, false);
        if (!isExchange()) {
            graphics.drawString(font, label("KARTY WSPÓLNE", "COMMUNITY"), 48, 27, TEXT, false);
            graphics.drawString(font, label("TWOJE", "YOURS"), 13, 52, TEXT, false);
        }
    }

    @Override
    protected void renderSlot(GuiGraphics graphics, Slot slot) {
        int index = slot.index;
        ItemStack stack = slot.getItem();
        if (index < 54 && stack.is(Items.BLACK_STAINED_GLASS_PANE)
            && " ".equals(stack.getHoverName().getString())) return;
        if (!isExchange() && isCardSlot(index) && !stack.isEmpty()) {
            String name = stack.getHoverName().getString();
            if (isCardName(name)) {
                drawCard(graphics, slot.x, slot.y, name);
                return;
            }
            if (name.equals("?")) {
                graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0xFF223648);
                graphics.fill(slot.x + 2, slot.y + 2, slot.x + 14, slot.y + 14, 0xFF314B60);
                graphics.drawString(font, "?", slot.x + 6, slot.y + 4, TEXT, false);
                return;
            }
        }
        super.renderSlot(graphics, slot);
    }

    private void drawCard(GuiGraphics graphics, int x, int y, String name) {
        boolean red = name.endsWith("♥") || name.endsWith("♦");
        String rank = name.substring(0, name.length() - 1);
        String suit = name.substring(name.length() - 1);
        graphics.fill(x, y, x + 16, y + 16, 0xFFEDDFC0);
        graphics.fill(x + 1, y + 1, x + 15, y + 15, 0xFFF8F4E8);
        graphics.drawString(font, rank, x + 2, y + 1, red ? 0xFF9C1E28 : 0xFF1C2D38, false);
        graphics.drawString(font, suit, x + 7, y + 8, red ? 0xFF9C1E28 : 0xFF1C2D38, false);
    }

    private void paintTurnPulse(GuiGraphics graphics, int x, int y) {
        long time = System.currentTimeMillis();
        int alpha = 0x55 + (int)(0x66 * (1 + Math.sin(time / 180.0)) / 2);
        int color = (alpha << 24) | 0xFFD15A;
        for (int index = 36; index <= 44; index++) {
            Slot seat = menu.getSlot(index);
            if (!seat.getItem().is(Items.GOLDEN_HELMET)) continue;
            graphics.fill(x + seat.x - 2, y + seat.y - 2,
                x + seat.x + 18, y + seat.y + 18, color);
            graphics.fill(x + seat.x - 1, y + seat.y - 1,
                x + seat.x + 17, y + seat.y + 17, FELT);
        }
    }

    private void paintCardReveals(GuiGraphics graphics, int x, int y) {
        long now = System.currentTimeMillis();
        int[] slots = {11, 12, 13, 14, 15, 19, 20};
        for (int index = 0; index < slots.length; index++) {
            Slot card = menu.getSlot(slots[index]);
            String name = card.getItem().isEmpty() ? "" : card.getItem().getHoverName().getString();
            if (!name.equals(previousCards[index])) {
                if (isCardName(name)) revealAt[index] = now;
                previousCards[index] = name;
            }
            long age = now - revealAt[index];
            if (age < 0 || age >= 700 || !isCardName(name)) continue;
            int alpha = (int)(180 * (1 - age / 700.0));
            graphics.fill(x + card.x - 2, y + card.y - 2,
                x + card.x + 18, y + card.y + 18, (alpha << 24) | 0xF3D588);
        }
    }

    private void paintSidebar(GuiGraphics graphics, int x, int y) {
        int sx = x + 178;
        int right = x + imageWidth - 5;
        graphics.fill(sx, y + 5, right, y + 217, 0xFF24291F);
        graphics.fill(sx + 1, y + 6, right - 1, y + 7, BORDER);
        graphics.drawString(font, label("STAN GRY", "GAME STATE"), sx + 5, y + 12, 0xFFE6C783, false);
        drawTrimmed(graphics, menu.getSlot(4).getItem().getHoverName().getString(), sx + 5, y + 25, 91, TEXT);
        drawTrimmed(graphics, menu.getSlot(8).getItem().getHoverName().getString(), sx + 5, y + 37, 91, MUTED);
        ItemStack turn = menu.getSlot(49).getItem();
        if (turn.is(Items.GOLDEN_HELMET)) {
            graphics.fill(sx + 3, y + 51, right - 3, y + 75, 0xFF4C3B21);
            graphics.drawString(font, label("TERAZ GRA", "ACTING NOW"), sx + 6, y + 54, 0xFFFFDA80, false);
            drawTrimmed(graphics, turn.getHoverName().getString(), sx + 6, y + 65, 88, TEXT);
        }
        graphics.drawString(font, label("OSTATNIE", "RECENT"), sx + 5, y + 84, 0xFFE6C783, false);
        ItemLore lore = menu.getSlot(0).getItem().get(DataComponents.LORE);
        List<Component> lines = lore == null ? List.of() : lore.lines();
        int first = Math.max(1, lines.size() - 8); // skip the fixed help line
        int lineY = y + 97;
        for (int i = first; i < lines.size() && lineY < y + 182; i++) {
            drawTrimmed(graphics, lines.get(i).getString(), sx + 5, lineY, 91, MUTED);
            lineY += 10;
        }
        graphics.fill(sx + 3, y + 190, right - 3, y + 191, BORDER);
        graphics.drawString(font, label("Najedź po opis", "Hover for details"), sx + 5, y + 198, MUTED, false);
        graphics.drawString(font, label("Kliknij, by zagrać", "Click to act"), sx + 5, y + 208, MUTED, false);
    }

    private void drawTrimmed(GuiGraphics graphics, String value, int x, int y, int maxWidth, int color) {
        graphics.drawString(font, font.plainSubstrByWidth(value, maxWidth), x, y, color, false);
    }

    private boolean isExchange() { return menu.getSlot(4).getItem().is(Items.EMERALD); }

    private boolean isCardSlot(int index) {
        return index >= 11 && index <= 15 || index == 19 || index == 20;
    }

    private static boolean isCardName(String name) {
        if (name == null || name.length() < 2 || name.length() > 3) return false;
        char suit = name.charAt(name.length() - 1);
        if (suit != '♣' && suit != '♦' && suit != '♥' && suit != '♠') return false;
        String rank = name.substring(0, name.length() - 1);
        return rank.equals("A") || rank.equals("K") || rank.equals("Q") || rank.equals("J")
            || rank.equals("10") || rank.length() == 1 && rank.charAt(0) >= '2' && rank.charAt(0) <= '9';
    }

    private String label(String polish, String english) {
        return title.getString().equals("Poker table") ? english : polish;
    }
}
