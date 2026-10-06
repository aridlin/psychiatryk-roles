package pl.aridlin.psychiatrykroles;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Comparator;
import java.util.Map;

final class PokerMenu extends ChestMenu {
    private static final int SIZE = 54;
    private final SimpleContainer display;
    private final ServerPlayer viewer;
    private boolean exchangeScreen;
    private int stockPage;
    private final java.util.Map<Integer,Long> displayedLots=new java.util.HashMap<>();
    private String lastState = "";

    // The client receives the same 54 server-synced slots, but never reads
    // PokerData. Keeping this slot contract stable lets the visual client lag
    // behind routine server-side poker changes.
    PokerMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, null, new SimpleContainer(SIZE));
    }

    PokerMenu(int containerId, Inventory inventory, ServerPlayer viewer) {
        this(containerId, inventory, viewer, new SimpleContainer(SIZE));
    }

    private PokerMenu(int containerId, Inventory inventory, ServerPlayer viewer, SimpleContainer display) {
        super(PokerMenus.TYPE.get(), containerId, inventory, display, 6);
        this.display = display;
        this.viewer = viewer;
        if (viewer != null) refresh();
    }

    @Override public boolean stillValid(Player player) { return true; }

    @Override public void broadcastChanges() {
        if (viewer == null) { super.broadcastChanges(); return; }
        String current = stateKey();
        if (!current.equals(lastState)) refresh(); else super.broadcastChanges();
    }

    @Override public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if(player!=viewer||slotId<0||(clickType!=ClickType.PICKUP&&clickType!=ClickType.QUICK_MOVE))return;
        if(exchangeScreen&&slotId>=SIZE&&slotId<this.slots.size()){
            var held=this.slots.get(slotId).getItem();
            PokerCommands.exchangeInStack(viewer,held,clickType==ClickType.QUICK_MOVE?-1:1);
            if(viewer.containerMenu==this)refresh();return;
        }
        if(slotId>=SIZE)return;
        if (slotId == 53) { viewer.closeContainer(); return; }
        if (exchangeScreen) {
            if(slotId==45)exchangeScreen=false;
            else if(slotId==46)PokerCommands.exchangeIn(viewer,-1);
            else if(slotId==48)stockPage=Math.max(0,stockPage-1);
            else if(slotId==50)stockPage++;
            else if(slotId>=9&&slotId<=44){
                Long id=displayedLots.get(slotId);
                if(id!=null){var lot=PokerData.get(viewer.getServer()).stock().stream().filter(l->l.id()==id).findFirst();
                    if(lot.isPresent())PokerCommands.withdrawLot(viewer,id,clickType==ClickType.QUICK_MOVE?(int)Math.min(lot.get().count(),lot.get().sample().getMaxStackSize()):1);
                }
            }
            if (viewer.containerMenu == this) refresh();
            return;
        }
        if (slotId == 46) { exchangeScreen = true; refresh(); return; }
        PokerCommands.guiClick(viewer, slotId);
        if (viewer.containerMenu == this) refresh();
    }

    void refresh() {
        display.clearContent();
        boolean en = PsychiatrykRoles.isEnglish(viewer);
        PokerData data = PokerData.get(viewer.getServer());
        PokerGame game = data.tableFor(viewer.getUUID());
        display.setItem(53, icon(Items.BARRIER, ChatFormatting.RED + (en ? "Close" : "Zamknij")));
        if (exchangeScreen) renderExchange(data, en);
        else {
            fillBorders(en);
            display.setItem(4, icon(Items.NETHER_STAR, ChatFormatting.GOLD + "TEXAS HOLD'EM"));
            if (game == null) renderLobby(data, en); else renderTable(game, data, en);
            display.setItem(46, icon(Items.EMERALD, ChatFormatting.GREEN
                + (en ? "Deposit / cash out items" : "Wpłać / wypłać przedmioty")));
        }
        display.setItem(0, activityIcon(en));
        lastState = stateKey();
        super.broadcastChanges();
    }

    private String stateKey() {
        return PsychiatrykRoles.isEnglish(viewer) + ":" + PokerActivity.recent(viewer.getUUID()).hashCode()
            + ":" + animationFrame() + ":" + gameStateKey();
    }

    private int animationFrame() {
        PokerGame game = PokerData.get(viewer.getServer()).tableFor(viewer.getUUID());
        return !exchangeScreen && game != null && game.turnPlayer() != null
            ? viewer.getServer().getTickCount() / 10 % 2 : 0;
    }

    private String gameStateKey() {
        PokerData data = PokerData.get(viewer.getServer()); PokerGame game = data.tableFor(viewer.getUUID());
        if (exchangeScreen) return "exchange:" + data.stockRevision()+":"+stockPage+":"+viewer.getInventory().items.stream().map(stack->stack.getCount()+":"+ItemStack.hashItemAndComponents(stack)).toList()+":" + data.balance(viewer.getUUID()) + ':'
            + BuiltInRegistries.ITEM.getKey(viewer.getMainHandItem().getItem()) + ':'
            + viewer.getMainHandItem().getCount() + ':' + viewer.getMainHandItem().getComponentsPatch();
        if (game == null) return "lobby:" + data.balance(viewer.getUUID()) + ':'
            + data.tables().stream().map(table -> table.id() + ':' + table.phase() + ':' + table.players().size()).toList();
        StringBuilder value = new StringBuilder(game.id()).append('|').append(game.phase()).append('|').append(game.pot())
            .append('|').append(game.currentBet()).append('|').append(game.redeemableReserve()).append('|').append(data.balance(viewer.getUUID()))
            .append('|').append(game.board()).append('|').append(game.turnPlayer() == null ? "-" : game.turnPlayer().id);
        for (PokerGame.PlayerState seat : game.players()) value.append('|').append(seat.id).append(':').append(seat.chips)
            .append(':').append(seat.committedHand).append(':').append(seat.folded).append(':').append(seat.allIn).append(':').append(seat.connected);
        PokerGame.PlayerState self = game.player(viewer.getUUID()); if (self != null) value.append('|').append(self.hole);
        return value.toString();
    }

    private void renderExchange(PokerData data,boolean en){
        displayedLots.clear();
        var lots=data.stock().stream().sorted(java.util.Comparator.comparing((PokerData.StockView l)->l.sample().getHoverName().getString()).thenComparingLong(PokerData.StockView::id)).toList();
        int pages=Math.max(1,(lots.size()+35)/36);stockPage=Math.clamp(stockPage,0,pages-1);
        display.setItem(4,icon(Items.CHEST,ChatFormatting.GOLD+(en?"SERVER STOCK — CASH OUT":"MAGAZYN SERWERA — WYPŁATA")));
        display.setItem(8,icon(Items.GOLD_NUGGET,ChatFormatting.YELLOW+(en?"Wallet: ":"Portfel: ")+data.balance(viewer.getUUID())+" EMC"));
        display.setItem(45,icon(Items.ARROW,ChatFormatting.YELLOW+(en?"Back to poker":"Wróć do pokera")));
        display.setItem(46,icon(Items.HOPPER,ChatFormatting.GREEN+(en?"DEPOSIT held stack":"WPŁAĆ stos z ręki")));
        display.setItem(47,icon(Items.BOOK,ChatFormatting.WHITE+(en?"Inventory: click deposits 1; Shift-click deposits stack":"Ekwipunek: klik wpłaca 1; Shift+klik wpłaca stos")));
        display.setItem(48,icon(Items.ARROW,ChatFormatting.YELLOW+(en?"Previous page":"Poprzednia strona")));
        display.setItem(49,icon(Items.PAPER,ChatFormatting.WHITE+(en?"Page ":"Strona ")+(stockPage+1)+" / "+pages));
        display.setItem(50,icon(Items.ARROW,ChatFormatting.YELLOW+(en?"Next page":"Następna strona")));
        display.setItem(52,icon(Items.BOOK,ChatFormatting.GRAY+(en?"Stock: click withdraws 1; Shift-click withdraws stack":"Zapas: klik wypłaca 1; Shift+klik wypłaca stos")));
        for(int i=0;i<36&&stockPage*36+i<lots.size();i++){
            var lot=lots.get(stockPage*36+i);var shown=lot.sample().copyWithCount((int)Math.min(lot.count(),lot.sample().getMaxStackSize()));
            var lore=new java.util.ArrayList<Component>();var old=shown.get(DataComponents.LORE);if(old!=null)lore.addAll(old.lines());
            lore.add(Component.literal((en?"Available: ":"Dostępne: ")+lot.count()+" | "+lot.unit()+" EMC / "+(en?"item":"szt.")).withStyle(ChatFormatting.AQUA));
            lore.add(Component.literal(en?"Only deposited items. Exact original components.":"Tylko wpłacone przedmioty. Oryginalne komponenty.").withStyle(ChatFormatting.GRAY));
            lore.add(Component.literal(en?"Click: 1 • Shift-click: stack":"Klik: 1 • Shift+klik: stos").withStyle(ChatFormatting.GREEN));
            shown.set(DataComponents.LORE,new ItemLore(lore));display.setItem(9+i,shown);displayedLots.put(9+i,lot.id());
        }
        if(lots.isEmpty())display.setItem(22,icon(Items.GRAY_STAINED_GLASS_PANE,ChatFormatting.GRAY+(en?"Stock empty — deposit an item below":"Magazyn pusty — wpłać przedmiot z ekwipunku")));
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

        PokerGame.PlayerState turn = game.turnPlayer();
        int seatSlot = 36;
        for (PokerGame.PlayerState seat : game.players()) {
            String flags = (seat.bot ? " [BOT]" : "") + (seat.folded ? (en ? " folded" : " pas") : "") + (seat.allIn ? " ALL-IN" : "");
            boolean acting = turn != null && seat.id.equals(turn.id);
            ItemStack seatIcon = icon(acting ? Items.GOLDEN_HELMET : Items.PLAYER_HEAD,
                (acting ? ChatFormatting.GOLD : seat.id.equals(viewer.getUUID()) ? ChatFormatting.AQUA : ChatFormatting.WHITE)
                    + (acting ? "▶ " : "") + seat.name
                    + ChatFormatting.GRAY + " | " + seat.chips + " | " + seat.committedHand + flags);
            if (acting) {
                lore(seatIcon, List.of(Component.literal(en ? "Acting now — waiting for this player." : "Teraz gra — czekamy na ten ruch.")
                    .withStyle(ChatFormatting.YELLOW)));
                if (animationFrame() == 0) {
                    seatIcon.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
                }
            }
            display.setItem(seatSlot++, seatIcon);
            if (seatSlot > 44) break;
        }
        if (turn != null) {
            display.setItem(49, icon(Items.GOLDEN_HELMET, ChatFormatting.YELLOW + (en ? "Turn: " : "Ruch: ") + turn.name));
            Item pulse = animationFrame() == 0 ? Items.YELLOW_STAINED_GLASS_PANE : Items.ORANGE_STAINED_GLASS_PANE;
            for (int slot : new int[] {48, 50}) display.setItem(slot, icon(pulse,
                ChatFormatting.GOLD + (turn.id.equals(viewer.getUUID()) ? (en ? "Your turn!" : "Twój ruch!") : (en ? "Waiting for " : "Czekamy na ") + turn.name)));
        }
    }

    private ItemStack activityIcon(boolean en) {
        ItemStack item = icon(Items.WRITABLE_BOOK, ChatFormatting.AQUA + (en ? "Recent poker messages" : "Ostatnie wiadomości pokera"));
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(en ? "Newest messages at the bottom. Help: /poker help" : "Najnowsze na dole. Pomoc: /poker help").withStyle(ChatFormatting.DARK_GRAY));
        List<Component> recent = PokerActivity.recent(viewer.getUUID());
        if (recent.isEmpty()) lines.add(Component.literal(en ? "No messages yet." : "Brak wiadomości."));
        // Short lore lines keep long status messages inside the screen.
        for (Component message : recent) {
            String text = message.getString();
            while (text.length() > 60) {
                int split = text.lastIndexOf(' ', 60);
                if (split < 20) split = 60;
                lines.add(Component.literal(text.substring(0, split)).setStyle(message.getStyle()));
                text = text.substring(split).stripLeading();
            }
            lines.add(Component.literal(text).setStyle(message.getStyle()));
        }
        if (lines.size() > 19) lines = new ArrayList<>(lines.subList(lines.size() - 19, lines.size()));
        lore(item, lines);
        return item;
    }

    private static void lore(ItemStack item, List<Component> lines) {
        item.set(DataComponents.LORE, new ItemLore(lines));
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
        ItemStack stack = new ItemStack(item); stack.set(DataComponents.CUSTOM_NAME, Component.literal(name)); return stack;
    }
}
