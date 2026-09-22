package pl.aridlin.psychiatrykroles;

import net.minecraft.ChatFormatting;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

final class PokerMenu extends ChestMenu {
    private static final int SIZE = 54;
    private final SimpleContainer display;
    private final ServerPlayer viewer;
    private String lastState = "";

    PokerMenu(int containerId, Inventory inventory, ServerPlayer viewer) {
        this(containerId, inventory, viewer, new SimpleContainer(SIZE));
    }

    private PokerMenu(int containerId, Inventory inventory, ServerPlayer viewer, SimpleContainer display) {
        super(MenuType.GENERIC_9x6, containerId, inventory, display, 6);
        this.display = display;
        this.viewer = viewer;
        refresh();
    }

    @Override public boolean stillValid(Player player) { return true; }

    @Override public void broadcastChanges() {
        if (viewer == null) { super.broadcastChanges(); return; }
        String current = stateKey();
        if (!current.equals(lastState)) refresh(); else super.broadcastChanges();
    }

    @Override public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (player != viewer || slotId < 0 || slotId >= SIZE) return;
        if (slotId == 53) { viewer.closeContainer(); return; }
        PokerCommands.guiClick(viewer, slotId);
        if (viewer.containerMenu == this) refresh();
    }

    void refresh() {
        display.clearContent();
        boolean en = RoleData.get(viewer.getServer()).isEnglish(viewer.getUUID());
        PokerData data = PokerData.get(viewer.getServer());
        PokerGame game = data.tableFor(viewer.getUUID());
        fillBorders(en);
        display.setItem(4, icon(Items.NETHER_STAR, ChatFormatting.GOLD + (en ? "TEXAS HOLD'EM" : "TEXAS HOLD'EM")));
        display.setItem(53, icon(Items.BARRIER, ChatFormatting.RED + (en ? "Close" : "Zamknij")));
        if (game == null) renderLobby(data, en); else renderTable(game, data, en);
        lastState = stateKey();
        super.broadcastChanges();
    }

    private String stateKey() {
        PokerData data = PokerData.get(viewer.getServer()); PokerGame game = data.tableFor(viewer.getUUID());
        if (game == null) return "lobby:" + data.tables().stream().map(table -> table.id() + ':' + table.phase() + ':' + table.players().size()).toList();
        StringBuilder value = new StringBuilder(game.id()).append('|').append(game.phase()).append('|').append(game.pot())
            .append('|').append(game.currentBet()).append('|').append(game.redeemableReserve()).append('|').append(data.balance(viewer.getUUID()))
            .append('|').append(game.board()).append('|').append(game.turnPlayer() == null ? "-" : game.turnPlayer().id);
        for (PokerGame.PlayerState seat : game.players()) value.append('|').append(seat.id).append(':').append(seat.chips)
            .append(':').append(seat.committedHand).append(':').append(seat.folded).append(':').append(seat.allIn).append(':').append(seat.connected);
        PokerGame.PlayerState self = game.player(viewer.getUUID()); if (self != null) value.append('|').append(self.hole);
        return value.toString();
    }

    private void renderLobby(PokerData data, boolean en) {
        display.setItem(13, icon(Items.OAK_SIGN, ChatFormatting.YELLOW + (en ? "Poker lobby" : "Lobby pokera")));
        display.setItem(49, icon(Items.CRAFTING_TABLE, ChatFormatting.GREEN + (en ? "Create and join a new table" : "Utwórz i dołącz do nowego stołu")));
        int slot = 18;
        for (PokerGame table : data.tables()) {
            if (slot > 44) break;
            display.setItem(slot++, icon(Items.GREEN_CARPET, ChatFormatting.AQUA + table.id() + ChatFormatting.GRAY
                + " | " + table.players().size() + "/9 | " + table.phase().name().toLowerCase()));
        }
        display.setItem(47, icon(Items.BOOK, ChatFormatting.GRAY + (en ? "Click a green table to join" : "Kliknij zielony stół, aby dołączyć")));
    }

    private void renderTable(PokerGame game, PokerData data, boolean en) {
        PokerGame.PlayerState self = game.player(viewer.getUUID());
        display.setItem(0, icon(Items.WRITABLE_BOOK, ChatFormatting.YELLOW + (en ? "Help: /poker help" : "Pomoc: /poker help")));
        display.setItem(4, icon(Items.NETHER_STAR, ChatFormatting.GOLD + game.id() + ChatFormatting.GRAY + " | "
            + game.phase().name().toLowerCase() + " | " + (en ? "pot " : "pula ") + game.pot()));
        display.setItem(8, icon(Items.GOLD_NUGGET, ChatFormatting.YELLOW + (en ? "Wallet " : "Portfel ") + data.balance(viewer.getUUID())
            + ChatFormatting.GRAY + " | " + (en ? "table " : "stół ") + (self == null ? 0 : self.chips)
            + " | " + (en ? "redeemable reserve " : "rezerwa wypłat ") + game.redeemableReserve()));

        List<PokerCard> board = game.board();
        for (int i = 0; i < 5; i++) display.setItem(11 + i, i < board.size() ? card(board.get(i)) : icon(Items.GRAY_STAINED_GLASS_PANE, ChatFormatting.DARK_GRAY + "?"));
        if (self != null && self.hole.size() == 2) {
            display.setItem(19, card(self.hole.get(0))); display.setItem(20, card(self.hole.get(1)));
        } else {
            display.setItem(19, icon(Items.BLACK_STAINED_GLASS_PANE, ChatFormatting.DARK_GRAY + "?"));
            display.setItem(20, icon(Items.BLACK_STAINED_GLASS_PANE, ChatFormatting.DARK_GRAY + "?"));
        }

        if (game.phase() == PokerGame.Phase.WAITING) {
            display.setItem(27, icon(Items.EMERALD, ChatFormatting.GREEN + (en ? "Exchange held stack into wallet" : "Wymień stos z ręki na żetony")));
            display.setItem(28, icon(Items.IRON_NUGGET, ChatFormatting.GREEN + (en ? "Buy in: 100" : "Buy-in: 100")));
            display.setItem(29, icon(Items.GOLD_NUGGET, ChatFormatting.GREEN + (en ? "Buy in: all wallet chips" : "Buy-in: cały portfel")));
            display.setItem(30, icon(Items.CHEST, ChatFormatting.YELLOW + (en ? "Cash out table stack" : "Wypłać stos ze stołu")));
            display.setItem(31, icon(Items.PLAYER_HEAD, ChatFormatting.AQUA + (en ? "Add one free bot" : "Dodaj darmowego bota")));
            display.setItem(32, icon(Items.SKELETON_SKULL, ChatFormatting.GRAY + (en ? "Remove sponsored bots" : "Usuń swoje boty")));
            display.setItem(33, icon(Items.LIME_DYE, ChatFormatting.GREEN + (en ? "Start hand" : "Rozpocznij rozdanie")));
            display.setItem(34, icon(Items.OAK_DOOR, ChatFormatting.RED + (en ? "Leave table" : "Opuść stół")));
        } else {
            display.setItem(27, icon(Items.LIME_DYE, ChatFormatting.GREEN + (en ? "Check" : "Czekaj")));
            display.setItem(28, icon(Items.GOLD_INGOT, ChatFormatting.YELLOW + (en ? "Call" : "Sprawdź")));
            display.setItem(29, icon(Items.DIAMOND, ChatFormatting.AQUA + (en ? "Raise by 20" : "Podbij o 20")));
            display.setItem(30, icon(Items.EMERALD_BLOCK, ChatFormatting.GREEN + (en ? "Raise by 100" : "Podbij o 100")));
            display.setItem(31, icon(Items.TNT, ChatFormatting.RED + "ALL-IN"));
            display.setItem(32, icon(Items.RED_DYE, ChatFormatting.RED + (en ? "Fold" : "Pas")));
        }

        int seatSlot = 36;
        for (PokerGame.PlayerState seat : game.players()) {
            String flags = (seat.bot ? " [BOT]" : "") + (seat.folded ? (en ? " folded" : " pas") : "") + (seat.allIn ? " ALL-IN" : "");
            display.setItem(seatSlot++, icon(seat.id.equals(viewer.getUUID()) ? Items.PLAYER_HEAD : Items.ARMOR_STAND,
                (seat.id.equals(viewer.getUUID()) ? ChatFormatting.AQUA : ChatFormatting.WHITE) + seat.name
                    + ChatFormatting.GRAY + " | " + seat.chips + " | " + seat.committedHand + flags));
            if (seatSlot > 44) break;
        }
        PokerGame.PlayerState turn = game.turnPlayer();
        if (turn != null) display.setItem(49, icon(Items.CLOCK, ChatFormatting.YELLOW + (en ? "Turn: " : "Ruch: ") + turn.name));
    }

    private void fillBorders(boolean en) {
        ItemStack pane = icon(Items.BLACK_STAINED_GLASS_PANE, " ");
        for (int slot : new int[]{1,2,3,5,6,7,9,10,16,17,45,46,48,50,51,52}) display.setItem(slot, pane.copy());
    }

    private static ItemStack card(PokerCard card) {
        Item item = switch (card.suit()) {
            case HEARTS -> Items.RED_DYE; case DIAMONDS -> Items.DIAMOND; case CLUBS -> Items.COAL; case SPADES -> Items.IRON_NUGGET;
        };
        ChatFormatting color = card.suit() == PokerCard.Suit.HEARTS || card.suit() == PokerCard.Suit.DIAMONDS ? ChatFormatting.RED : ChatFormatting.WHITE;
        return icon(item, color + card.shortName());
    }

    private static ItemStack icon(Item item, String name) {
        ItemStack stack = new ItemStack(item); stack.setHoverName(Component.literal(name)); return stack;
    }
}
