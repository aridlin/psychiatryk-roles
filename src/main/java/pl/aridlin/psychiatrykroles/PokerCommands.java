package pl.aridlin.psychiatrykroles;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;

final class PokerCommands {
    private PokerCommands() {}

    static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("poker")
            .executes(context -> help(context.getSource().getPlayerOrException()))
            .then(Commands.literal("help").executes(context -> help(context.getSource().getPlayerOrException())))
            .then(Commands.literal("gui").executes(context -> openGui(context.getSource().getPlayerOrException())))
            .then(Commands.literal("list").executes(context -> list(context.getSource())))
            .then(Commands.literal("create").then(Commands.argument("table", StringArgumentType.word())
                .executes(context -> create(context.getSource().getPlayerOrException(), StringArgumentType.getString(context, "table")))))
            .then(Commands.literal("join").then(Commands.argument("table", StringArgumentType.word())
                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                    PokerData.get(context.getSource().getServer()).tables().stream().map(PokerGame::id), builder))
                .executes(context -> join(context.getSource().getPlayerOrException(), StringArgumentType.getString(context, "table")))))
            .then(Commands.literal("leave").executes(context -> leave(context.getSource().getPlayerOrException())))
            .then(Commands.literal("start").executes(context -> start(context.getSource().getPlayerOrException())))
            .then(Commands.literal("status").executes(context -> status(context.getSource().getPlayerOrException())))
            .then(Commands.literal("cards").executes(context -> cards(context.getSource().getPlayerOrException())))
            .then(Commands.literal("values").executes(context -> values(context.getSource().getPlayerOrException())))
            .then(Commands.literal("bank").executes(context -> bank(context.getSource().getPlayerOrException())))
            .then(Commands.literal("exchange")
                .then(Commands.literal("in")
                    .executes(context -> exchangeIn(context.getSource().getPlayerOrException(), -1))
                    .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                        .executes(context -> exchangeIn(context.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(context, "count")))))
                .then(Commands.literal("out").then(Commands.argument("item", ResourceLocationArgument.id())
                    .suggests((context, builder) -> SharedSuggestionProvider.suggest(PokerItemValues.all().keySet(), builder))
                    .executes(context -> exchangeOut(context.getSource().getPlayerOrException(), ResourceLocationArgument.getId(context, "item"), 1))
                    .then(Commands.argument("count", IntegerArgumentType.integer(1, 2304))
                        .executes(context -> exchangeOut(context.getSource().getPlayerOrException(), ResourceLocationArgument.getId(context, "item"), IntegerArgumentType.getInteger(context, "count")))))))
            .then(Commands.literal("buyin").then(Commands.argument("chips", IntegerArgumentType.integer(1, 10_000_000))
                .executes(context -> buyIn(context.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(context, "chips")))))
            .then(Commands.literal("cashout").executes(context -> cashOut(context.getSource().getPlayerOrException())))
            .then(Commands.literal("bots")
                .then(Commands.literal("add").then(Commands.argument("count", IntegerArgumentType.integer(1, 4))
                    .executes(context -> addBots(context.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(context, "count")))))
                .then(Commands.literal("remove").executes(context -> removeBots(context.getSource().getPlayerOrException()))))
            .then(Commands.literal("check").executes(context -> act(context.getSource().getPlayerOrException(), "check", PokerGame::check)))
            .then(Commands.literal("call").executes(context -> act(context.getSource().getPlayerOrException(), "call", PokerGame::call)))
            .then(Commands.literal("raise").then(Commands.argument("amount", IntegerArgumentType.integer(1))
                .executes(context -> raise(context.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(context, "amount")))))
            .then(Commands.literal("fold").executes(context -> act(context.getSource().getPlayerOrException(), "fold", PokerGame::fold)))
            .then(Commands.literal("allin").executes(context -> act(context.getSource().getPlayerOrException(), "allin", PokerGame::allIn)))
            .then(Commands.literal("admin").requires(source -> source.hasPermission(4))
                .then(Commands.literal("reset").then(Commands.argument("table", StringArgumentType.word())
                    .executes(context -> adminReset(context.getSource(), StringArgumentType.getString(context, "table")))))
                .then(Commands.literal("delete").then(Commands.argument("table", StringArgumentType.word())
                    .executes(context -> adminDelete(context.getSource(), StringArgumentType.getString(context, "table")))))));
    }

    static void onLogin(ServerPlayer player) {
        PokerGame game = PokerData.get(player.getServer()).tableFor(player.getUUID());
        if (game == null) return;
        game.reconnect(player.getUUID(), player.getGameProfile().getName());
        game.recoverDisconnectedTurns();
        PokerData.get(player.getServer()).changed();
        send(player, "Połączono ponownie ze stołem " + game.id() + ".", "Reconnected to table " + game.id() + ".", ChatFormatting.GREEN);
        publishAfterMutation(game, player.getServer());
        sendCards(player, game);
    }

    static void onLogout(ServerPlayer player) {
        PokerData data = PokerData.get(player.getServer());
        PokerGame game = data.tableFor(player.getUUID());
        if (game == null) return;
        game.disconnect(player.getUUID());
        data.changed();
        broadcast(game, player.getServer(), player.getGameProfile().getName() + " rozłączył(a) się.",
            player.getGameProfile().getName() + " disconnected.", ChatFormatting.YELLOW);
        publishAutomaticFolds(game, player.getServer());
        broadcastStatus(game, player.getServer());
    }

    private static int help(ServerPlayer player) {
        boolean en = english(player);
        player.sendSystemMessage(Component.literal(en ? "TEXAS HOLD'EM — COMMANDS" : "TEXAS HOLD'EM — KOMENDY").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        String[] lines = en ? new String[] {
            "/poker list | create <table> | join <table> | leave",
            "/poker bank | exchange in [count] | exchange out <item> [count] | values",
            "/poker buyin <chips> | cashout | bots add <1-4> | bots remove",
            "/poker start | status | cards | check | call | raise <total> | fold | allin",
            "Exchange accepted items into your persistent chip wallet, or spend wallet chips on valued items. Bulk trash is worth 0.",
            "Buy-in moves wallet chips to the table; cashout moves the table stack back. Minimum first buy-in 100; blinds 10/20.",
            "Bots are free house seats. House chips are never redeemable, so bots cannot mint item value.",
            "Two to nine seats. Consultants may create and play normally; poker grants no world permissions."
        } : new String[] {
            "/poker list | create <stół> | join <stół> | leave",
            "/poker bank | exchange in [liczba] | exchange out <przedmiot> [liczba] | values",
            "/poker buyin <żetony> | cashout | bots add <1-4> | bots remove",
            "/poker start | status | cards | check | call | raise <łącznie> | fold | allin",
            "Wymieniaj uznawane przedmioty na trwałe saldo żetonów albo wydawaj żetony na przedmioty z tabeli. Śmieci masowe mają wartość 0.",
            "Buy-in przenosi żetony z portfela na stół, a cashout z powrotem. Pierwszy buy-in min. 100; ciemne 10/20.",
            "Boty są darmowymi miejscami kasyna. Żetonów kasyna nie można wypłacić, więc boty nie tworzą wartości przedmiotów.",
            "Od 2 do 9 miejsc. Konsultanci mogą tworzyć stoły i grać; poker nie daje uprawnień w świecie."
        };
        for (String line : lines) player.sendSystemMessage(Component.literal(line).withStyle(ChatFormatting.GRAY));
        return 1;
    }

    private static int list(CommandSourceStack source) {
        var tables = PokerData.get(source.getServer()).tables();
        if (tables.isEmpty()) {
            source.sendSuccess(() -> Component.literal(source.getEntity() instanceof ServerPlayer player && english(player)
                ? "No poker tables. Use /poker create <name>." : "Brak stołów. Użyj /poker create <nazwa>.").withStyle(ChatFormatting.GRAY), false);
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Poker: " + tables.size()).withStyle(ChatFormatting.GOLD), false);
        for (PokerGame game : tables) source.sendSuccess(() -> Component.literal("- " + game.id() + " | " + game.phase().name().toLowerCase(Locale.ROOT)
            + " | " + game.players().size() + "/9 | pot " + game.pot()).withStyle(ChatFormatting.GRAY), false);
        return tables.size();
    }

    private static int openGui(ServerPlayer player) {
        player.openMenu(new net.minecraft.world.SimpleMenuProvider(
            (containerId, inventory, ignored) -> new PokerMenu(containerId, inventory, player),
            Component.literal(english(player) ? "Poker table" : "Stół pokerowy")));
        return 1;
    }

    static void guiClick(ServerPlayer player, int slot) {
        PokerData data = PokerData.get(player.getServer()); PokerGame game = data.tableFor(player.getUUID());
        if (game == null) {
            if (slot == 49) {
                String base = "gui-" + player.getGameProfile().getName().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
                String id = base; int suffix = 2; while (data.table(id) != null) id = base + "-" + suffix++;
                create(player, id);
            } else if (slot >= 18 && slot <= 44) {
                List<PokerGame> tables = new ArrayList<>(data.tables()); int index = slot - 18;
                if (index < tables.size()) join(player, tables.get(index).id());
            }
            return;
        }
        if (game.phase() == PokerGame.Phase.WAITING) {
            switch (slot) {
                case 27 -> exchangeIn(player, -1);
                case 28 -> buyIn(player, 100);
                case 29 -> buyIn(player, (int)Math.min(10_000_000, data.balance(player.getUUID())));
                case 30 -> cashOut(player);
                case 31 -> addBots(player, 1);
                case 32 -> removeBots(player);
                case 33 -> start(player);
                case 34 -> leave(player);
                default -> { }
            }
        } else {
            switch (slot) {
                case 27 -> act(player, "check", PokerGame::check);
                case 28 -> act(player, "call", PokerGame::call);
                case 29 -> raise(player, game.currentBet() + 20);
                case 30 -> raise(player, game.currentBet() + 100);
                case 31 -> act(player, "allin", PokerGame::allIn);
                case 32 -> act(player, "fold", PokerGame::fold);
                default -> { }
            }
        }
    }

    private static int create(ServerPlayer player, String raw) {
        String id = raw.toLowerCase(Locale.ROOT);
        if (!id.matches("[a-z0-9_-]{1,24}")) { sendError(player, "invalid-table-name"); return 0; }
        PokerData data = PokerData.get(player.getServer());
        if (data.tableFor(player.getUUID()) != null) { sendError(player, "already-seated"); return 0; }
        try {
            PokerGame game = data.create(id, player.getUUID(), player.getGameProfile().getName());
            send(player, "Utworzono stół " + id + ". Zaproś graczy przez /poker join " + id + ".",
                "Created table " + id + ". Invite players with /poker join " + id + ".", ChatFormatting.GREEN);
            PsychiatrykRoles.pokerAudit(player, "POKER_CREATE", id);
            return game.players().size();
        } catch (PokerGame.PokerException error) { sendError(player, error.code); return 0; }
    }

    private static int join(ServerPlayer player, String id) {
        PokerData data = PokerData.get(player.getServer());
        PokerGame current = data.tableFor(player.getUUID());
        if (current != null && !current.id().equalsIgnoreCase(id)) { sendError(player, "already-seated"); return 0; }
        PokerGame game = data.table(id);
        if (game == null) { sendError(player, "unknown-table"); return 0; }
        try {
            game.join(player.getUUID(), player.getGameProfile().getName()); data.changed();
            broadcast(game, player.getServer(), player.getGameProfile().getName() + " dołącza do stołu.",
                player.getGameProfile().getName() + " joined the table.", ChatFormatting.GREEN);
            PsychiatrykRoles.pokerAudit(player, "POKER_JOIN", game.id());
            return 1;
        } catch (PokerGame.PokerException error) { sendError(player, error.code); return 0; }
    }

    private static int leave(ServerPlayer player) {
        PokerData data = PokerData.get(player.getServer()); PokerGame game = data.tableFor(player.getUUID());
        if (game == null) { sendError(player, "not-seated"); return 0; }
        try {
            game.leave(player.getUUID()); data.changed();
            broadcast(game, player.getServer(), player.getGameProfile().getName() + " opuszcza stół.",
                player.getGameProfile().getName() + " left the table.", ChatFormatting.YELLOW);
            if (game.players().isEmpty()) data.remove(game.id());
            publishAfterMutation(game, player.getServer());
            PsychiatrykRoles.pokerAudit(player, "POKER_LEAVE", game.id());
            return 1;
        } catch (PokerGame.PokerException error) { sendError(player, error.code); return 0; }
    }

    private static int start(ServerPlayer player) {
        PokerData data = PokerData.get(player.getServer()); PokerGame game = data.tableFor(player.getUUID());
        if (game == null) { sendError(player, "not-seated"); return 0; }
        if (!game.owner().equals(player.getUUID()) && !player.hasPermissions(4)) { sendError(player, "owner-only"); return 0; }
        try {
            game.start(new Random()); data.changed();
            broadcast(game, player.getServer(), "Rozdanie #" + game.handNumber() + " rozpoczęte.", "Hand #" + game.handNumber() + " started.", ChatFormatting.GOLD);
            for (PokerGame.PlayerState state : game.players()) {
                ServerPlayer online = player.getServer().getPlayerList().getPlayer(state.id);
                if (online != null) sendCards(online, game);
            }
            publishAfterMutation(game, player.getServer());
            PsychiatrykRoles.pokerAudit(player, "POKER_START", game.id() + " hand=" + game.handNumber());
            return 1;
        } catch (PokerGame.PokerException error) { sendError(player, error.code); return 0; }
    }

    private interface PlayerAction { void apply(PokerGame game, UUID player); }

    private static int act(ServerPlayer player, String action, PlayerAction operation) {
        PokerData data = PokerData.get(player.getServer()); PokerGame game = data.tableFor(player.getUUID());
        if (game == null) { sendError(player, "not-seated"); return 0; }
        try {
            operation.apply(game, player.getUUID()); data.changed();
            String pl = switch (action) { case "check" -> "czeka"; case "call" -> "sprawdza"; case "fold" -> "pasuje"; default -> "wchodzi all-in"; };
            String en = switch (action) { case "check" -> "checks"; case "call" -> "calls"; case "fold" -> "folds"; default -> "is all-in"; };
            broadcast(game, player.getServer(), player.getGameProfile().getName() + " " + pl + ".", player.getGameProfile().getName() + " " + en + ".", ChatFormatting.AQUA);
            publishAfterMutation(game, player.getServer());
            return 1;
        } catch (PokerGame.PokerException error) { sendError(player, error.code); return 0; }
    }

    private static int raise(ServerPlayer player, int amount) {
        PokerData data = PokerData.get(player.getServer()); PokerGame game = data.tableFor(player.getUUID());
        if (game == null) { sendError(player, "not-seated"); return 0; }
        try {
            game.raiseTo(player.getUUID(), amount); data.changed();
            broadcast(game, player.getServer(), player.getGameProfile().getName() + " podbija do " + amount + ".",
                player.getGameProfile().getName() + " raises to " + amount + ".", ChatFormatting.AQUA);
            publishAfterMutation(game, player.getServer()); return 1;
        } catch (PokerGame.PokerException error) { sendError(player, error.code); return 0; }
    }

    private static int bank(ServerPlayer player) {
        long balance = PokerData.get(player.getServer()).balance(player.getUUID());
        send(player, "Portfel pokerowy: " + balance + " żetonów.", "Poker wallet: " + balance + " chips.", ChatFormatting.GOLD);
        return (int) Math.min(Integer.MAX_VALUE, balance);
    }

    private static int exchangeIn(ServerPlayer player, int requestedCount) {
        PokerData data = PokerData.get(player.getServer()); ItemStack held = player.getMainHandItem();
        int count = requestedCount < 0 ? held.getCount() : requestedCount;
        if (held.isEmpty() || count > held.getCount()) { sendError(player, "hold-exchange-item"); return 0; }
        if (!PsychiatrykRoles.isPokerExchangeInputAllowed(held)) { sendError(player, "tagged-exchange-item"); return 0; }
        String id = BuiltInRegistries.ITEM.getKey(held.getItem()).toString(); int unit = PokerItemValues.value(id);
        if (unit <= 0) { sendError(player, "worthless-item"); return 0; }
        long credit;
        try { credit = Math.multiplyExact((long) unit, count); data.credit(player.getUUID(), credit); }
        catch (ArithmeticException error) { sendError(player, "buyin-too-large"); return 0; }
        held.shrink(count); player.inventoryMenu.broadcastChanges();
        send(player, "Wymieniono " + count + "× " + id + " na " + credit + " żetonów. Portfel: " + data.balance(player.getUUID()) + ".",
            "Exchanged " + count + "× " + id + " for " + credit + " chips. Wallet: " + data.balance(player.getUUID()) + ".", ChatFormatting.GREEN);
        PsychiatrykRoles.pokerAudit(player, "POKER_EXCHANGE_IN", "item=" + id + " count=" + count + " chips=" + credit); return (int)Math.min(Integer.MAX_VALUE, credit);
    }

    private static int exchangeOut(ServerPlayer player, ResourceLocation id, int count) {
        PokerData data = PokerData.get(player.getServer()); int unit = PokerItemValues.value(id.toString());
        var item = BuiltInRegistries.ITEM.getOptional(id);
        if (unit <= 0 || item.isEmpty()) { sendError(player, "worthless-item"); return 0; }
        long cost;
        try { cost = Math.multiplyExact((long) unit, count); } catch (ArithmeticException error) { sendError(player, "buyin-too-large"); return 0; }
        if (!data.debit(player.getUUID(), cost)) { sendError(player, "wallet-insufficient"); return 0; }
        int remaining = count;
        while (remaining > 0) {
            ItemStack delivery = new ItemStack(item.get(), Math.min(remaining, item.get().getMaxStackSize())); remaining -= delivery.getCount();
            if (!player.getInventory().add(delivery) && !delivery.isEmpty()) {
                ItemEntity dropped = player.drop(delivery, false); if (dropped != null) DroppedItemOwnership.mark(dropped, player.getUUID());
            }
        }
        send(player, "Kupiono " + count + "× " + id + " za " + cost + ". Portfel: " + data.balance(player.getUUID()) + ".",
            "Bought " + count + "× " + id + " for " + cost + ". Wallet: " + data.balance(player.getUUID()) + ".", ChatFormatting.GREEN);
        PsychiatrykRoles.pokerAudit(player, "POKER_EXCHANGE_OUT", "item=" + id + " count=" + count + " chips=" + cost); return count;
    }

    private static int buyIn(ServerPlayer player, int amount) {
        PokerData data = PokerData.get(player.getServer()); PokerGame game = data.tableFor(player.getUUID());
        if (game == null) { sendError(player, "not-seated"); return 0; }
        if (data.balance(player.getUUID()) < amount) { sendError(player, "wallet-insufficient"); return 0; }
        try {
            game.buyIn(player.getUUID(), amount); data.debit(player.getUUID(), amount); data.changed();
            send(player, "Przeniesiono " + amount + " żetonów na stół. Stół: " + game.player(player.getUUID()).chips + ", portfel: " + data.balance(player.getUUID()) + ".",
                "Moved " + amount + " chips to the table. Table: " + game.player(player.getUUID()).chips + ", wallet: " + data.balance(player.getUUID()) + ".", ChatFormatting.GREEN);
            PsychiatrykRoles.pokerAudit(player, "POKER_BUYIN", game.id() + " chips=" + amount); return amount;
        } catch (PokerGame.PokerException error) { sendError(player, error.code); return 0; }
    }

    private static int cashOut(ServerPlayer player) {
        PokerData data = PokerData.get(player.getServer()); PokerGame game = data.tableFor(player.getUUID());
        if (game == null) { sendError(player, "not-seated"); return 0; }
        try {
            PokerGame.CashOut result = game.cashOut(player.getUUID());
            if (result.paid() > 0) data.credit(player.getUUID(), result.paid()); data.changed();
            send(player, "Do portfela wróciło " + result.paid() + " żetonów. Żetony kasyna wygasłe: " + result.houseChipsExpired() + ".",
                "Returned " + result.paid() + " chips to your wallet. Expired house chips: " + result.houseChipsExpired() + ".", ChatFormatting.GREEN);
            PsychiatrykRoles.pokerAudit(player, "POKER_CASHOUT", game.id() + " paid=" + result.paid() + " house_expired=" + result.houseChipsExpired()); return result.paid();
        } catch (PokerGame.PokerException error) { sendError(player, error.code); return 0; }
    }

    private static int addBots(ServerPlayer player, int count) {
        PokerData data = PokerData.get(player.getServer()); PokerGame game = data.tableFor(player.getUUID());
        if (game == null) { sendError(player, "not-seated"); return 0; }
        if (!game.owner().equals(player.getUUID()) && !player.hasPermissions(4)) { sendError(player, "owner-only"); return 0; }
        try {
            game.addBots(player.getUUID(), count, 100); data.changed();
            broadcast(game, player.getServer(), "Dodano " + count + " darmowych botów kasyna.",
                "Added " + count + " free house bots.", ChatFormatting.GREEN);
            PsychiatrykRoles.pokerAudit(player, "POKER_BOTS_ADD", game.id() + " count=" + count); return count;
        } catch (PokerGame.PokerException error) { sendError(player, error.code); return 0; }
    }

    private static int removeBots(ServerPlayer player) {
        PokerData data = PokerData.get(player.getServer()); PokerGame game = data.tableFor(player.getUUID());
        if (game == null) { sendError(player, "not-seated"); return 0; }
        if (!game.owner().equals(player.getUUID()) && !player.hasPermissions(4)) { sendError(player, "owner-only"); return 0; }
        try {
            if (game.botCount() <= 0) { sendError(player, "no-bots"); return 0; }
            int removed = game.removeBots(player.getUUID());
            data.changed();
            send(player, "Usunięto boty; ich żetony kasyna wygasły.", "Removed bots; their house chips expired.", ChatFormatting.GREEN);
            PsychiatrykRoles.pokerAudit(player, "POKER_BOTS_REMOVE", game.id() + " house_chips=" + removed); return 1;
        } catch (PokerGame.PokerException error) { sendError(player, error.code); return 0; }
    }

    private static int values(ServerPlayer player) {
        boolean en = english(player);
        player.sendSystemMessage(Component.literal(en ? "POKER ITEM VALUES (per item)" : "WARTOŚCI POKEROWE (za sztukę)").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        PokerItemValues.all().entrySet().stream().sorted((a, b) -> Integer.compare(b.getValue(), a.getValue())).forEach(entry ->
            player.sendSystemMessage(Component.literal(entry.getKey() + " = " + entry.getValue()).withStyle(ChatFormatting.GRAY)));
        send(player, "Wymiana używa uproszczonych wartości w stylu ProjectE. Śmieci i przedmioty spoza listy mają wartość 0.",
            "Exchange uses simplified ProjectE-style values. Trash and unlisted items are worth 0.", ChatFormatting.YELLOW);
        return PokerItemValues.all().size();
    }

    private static int status(ServerPlayer player) {
        PokerGame game = PokerData.get(player.getServer()).tableFor(player.getUUID());
        if (game == null) { sendError(player, "not-seated"); return 0; }
        sendStatus(player, game); return 1;
    }

    private static int cards(ServerPlayer player) {
        PokerGame game = PokerData.get(player.getServer()).tableFor(player.getUUID());
        if (game == null) { sendError(player, "not-seated"); return 0; }
        sendCards(player, game); return 1;
    }

    private static int adminReset(CommandSourceStack source, String id) {
        PokerGame game = PokerData.get(source.getServer()).table(id); if (game == null) { source.sendFailure(Component.literal("Unknown table: " + id)); return 0; }
        game.reset(); PokerData.get(source.getServer()).changed(); broadcast(game, source.getServer(), "Stół zresetowano przez operatora.", "The table was reset by an operator.", ChatFormatting.RED);
        PsychiatrykRoles.pokerAudit(source.getServer(), source.getTextName(), "POKER_RESET", id); return 1;
    }

    private static int adminDelete(CommandSourceStack source, String id) {
        PokerGame game = PokerData.get(source.getServer()).table(id);
        if (game == null) { source.sendFailure(Component.literal("Unknown table: " + id)); return 0; }
        if (!game.canDelete()) { source.sendFailure(Component.literal("Table still has players; cash out/remove bots and leave first.")); return 0; }
        PokerData.get(source.getServer()).remove(id);
        source.sendSuccess(() -> Component.literal("Deleted poker table " + id).withStyle(ChatFormatting.RED), true);
        PsychiatrykRoles.pokerAudit(source.getServer(), source.getTextName(), "POKER_DELETE", id); return 1;
    }

    private static void publishAfterMutation(PokerGame game, MinecraftServer server) {
        runBots(game, server);
        publishAutomaticFolds(game, server);
        if (game.phase() == PokerGame.Phase.WAITING && !game.lastResult().isEmpty()) {
            publishResult(game, server); game.clearLastResult();
        }
        broadcastStatus(game, server);
    }

    private static void runBots(PokerGame game, MinecraftServer server) {
        Random random = new Random(game.handNumber() * 31L + game.pot() * 17L + game.currentBet());
        int guard = 0;
        while (game.phase() != PokerGame.Phase.WAITING && game.botTurn() && guard++ < 100) {
            PokerGame.PlayerState bot = game.turnPlayer(); String action = game.actBot(random);
            broadcast(game, server, bot.name + " wykonuje ruch: " + action + ".", bot.name + " acts: " + action + ".", ChatFormatting.GRAY);
        }
        if (guard >= 100) throw new IllegalStateException("Poker bot loop did not settle");
        PokerData.get(server).changed();
    }

    private static void publishAutomaticFolds(PokerGame game, MinecraftServer server) {
        for (UUID id : game.drainAutoFolded()) {
            PokerGame.PlayerState state = game.player(id); String name = state == null ? id.toString() : state.name;
            broadcast(game, server, name + " automatycznie pasuje po rozłączeniu.", name + " auto-folds after disconnecting.", ChatFormatting.YELLOW);
        }
    }

    private static void publishResult(PokerGame game, MinecraftServer server) {
        for (PokerGame.ShowdownEntry result : game.lastShowdown()) {
            if (result.payout() <= 0) continue;
            PokerGame.PlayerState state = game.player(result.player()); String name = state == null ? result.player().toString() : state.name;
            if (result.hand() == null) broadcast(game, server, name + " wygrywa " + result.payout() + " bez showdownu.", name + " wins " + result.payout() + " uncontested.", ChatFormatting.GOLD);
            else broadcast(game, server, name + " wygrywa " + result.payout() + " — " + handName(result.hand(), false) + ".",
                name + " wins " + result.payout() + " — " + handName(result.hand(), true) + ".", ChatFormatting.GOLD);
        }
    }

    private static void sendStatus(ServerPlayer player, PokerGame game) {
        boolean en = english(player);
        player.sendSystemMessage(Component.literal("♠ " + game.id() + " | " + phaseName(game.phase(), en) + " | " + (en ? "pot " : "pula ") + game.pot()
            + (game.phase() == PokerGame.Phase.WAITING ? "" : " | " + (en ? "bet " : "zakład ") + game.currentBet())).withStyle(ChatFormatting.GOLD));
        player.sendSystemMessage(Component.literal((en ? "Wallet: " : "Portfel: ") + PokerData.get(player.getServer()).balance(player.getUUID())).withStyle(ChatFormatting.YELLOW));
        player.sendSystemMessage(Component.literal((en ? "Board: " : "Stół: ") + cards(game.board())).withStyle(ChatFormatting.GREEN));
        for (PokerGame.PlayerState state : game.players()) {
            String flags = (state.bot ? " BOT" : "") + (state.folded ? (en ? " folded" : " pas") : "") + (state.allIn ? " ALL-IN" : "") + (!state.connected ? (en ? " offline" : " offline") : "");
            player.sendSystemMessage(Component.literal("- " + state.name + ": " + state.chips + " | " + (en ? "in hand " : "w rozdaniu ") + state.committedHand + flags)
                .withStyle(state.id.equals(player.getUUID()) ? ChatFormatting.AQUA : ChatFormatting.GRAY));
        }
        PokerGame.PlayerState turn = game.turnPlayer();
        if (turn != null) player.sendSystemMessage(Component.literal((en ? "Turn: " : "Ruch: ") + turn.name).withStyle(ChatFormatting.YELLOW));
    }

    private static void broadcastStatus(PokerGame game, MinecraftServer server) {
        for (PokerGame.PlayerState state : game.players()) {
            ServerPlayer player = server.getPlayerList().getPlayer(state.id); if (player != null) sendStatus(player, game);
        }
    }

    private static void sendCards(ServerPlayer player, PokerGame game) {
        PokerGame.PlayerState state = game.player(player.getUUID());
        if (state == null || state.hole.isEmpty()) { send(player, "Nie masz aktywnych kart.", "You have no active hole cards.", ChatFormatting.GRAY); return; }
        send(player, "Twoje karty: " + cards(state.hole), "Your cards: " + cards(state.hole), ChatFormatting.LIGHT_PURPLE);
    }

    private static String cards(List<PokerCard> cards) { return cards.isEmpty() ? "—" : String.join(" ", cards.stream().map(PokerCard::shortName).toList()); }

    private static void broadcast(PokerGame game, MinecraftServer server, String polish, String english, ChatFormatting color) {
        for (PokerGame.PlayerState state : game.players()) {
            ServerPlayer player = server.getPlayerList().getPlayer(state.id); if (player != null) send(player, polish, english, color);
        }
    }

    private static boolean english(ServerPlayer player) { return RoleData.get(player.getServer()).isEnglish(player.getUUID()); }
    private static void send(ServerPlayer player, String polish, String english, ChatFormatting color) {
        player.sendSystemMessage(Component.literal(english(player) ? english : polish).withStyle(color));
    }

    private static void sendError(ServerPlayer player, String code) {
        String[] text = switch (code) {
            case "invalid-table-name" -> new String[]{"Nazwa: 1–24 znaków a-z, 0-9, _ lub -.", "Name: 1–24 characters a-z, 0-9, _ or -."};
            case "table-exists" -> new String[]{"Taki stół już istnieje.", "That table already exists."};
            case "unknown-table" -> new String[]{"Nie ma takiego stołu.", "That table does not exist."};
            case "already-seated" -> new String[]{"Najpierw opuść obecny stół.", "Leave your current table first."};
            case "not-seated" -> new String[]{"Nie siedzisz przy stole pokerowym.", "You are not seated at a poker table."};
            case "hand-active" -> new String[]{"Rozdanie już trwa.", "A hand is already active."};
            case "table-full" -> new String[]{"Stół jest pełny.", "The table is full."};
            case "need-two-funded" -> new String[]{"Potrzeba co najmniej dwóch graczy z żetonami.", "At least two funded players are required."};
            case "owner-only" -> new String[]{"Tylko właściciel stołu lub operator może rozpocząć rozdanie.", "Only the table owner or an operator can start a hand."};
            case "no-hand" -> new String[]{"Nie trwa żadne rozdanie.", "No hand is active."};
            case "not-your-turn" -> new String[]{"To nie twój ruch.", "It is not your turn."};
            case "cannot-act" -> new String[]{"Nie możesz teraz wykonać ruchu.", "You cannot act now."};
            case "cannot-check" -> new String[]{"Musisz sprawdzić zakład, wejść all-in albo spasować.", "You must call, go all-in, or fold."};
            case "nothing-to-call" -> new String[]{"Nie ma czego sprawdzać; użyj /poker check.", "There is nothing to call; use /poker check."};
            case "invalid-raise" -> new String[]{"Nieprawidłowa łączna wysokość podbicia.", "Invalid total raise amount."};
            case "raise-too-small" -> new String[]{"Podbicie jest za małe, chyba że to all-in.", "The raise is too small unless it is all-in."};
            case "raise-not-reopened" -> new String[]{"Niepełny all-in nie otworzył ponownie możliwości podbicia; możesz sprawdzić lub spasować.", "The incomplete all-in did not reopen raising; you may call or fold."};
            case "already-all-in" -> new String[]{"Nie masz już żetonów do postawienia.", "You have no chips left to wager."};
            case "cashout-first" -> new String[]{"Najpierw wypłać żetony przez /poker cashout.", "Cash out your chips with /poker cashout first."};
            case "hold-exchange-item" -> new String[]{"Trzymaj wymieniany przedmiot w głównej ręce (i podaj prawidłową liczbę).", "Hold the exchange item in your main hand (and use a valid count)."};
            case "worthless-item" -> new String[]{"Ten przedmiot nie ma wartości pokerowej. Sprawdź /poker values.", "That item has no poker value. See /poker values."};
            case "tagged-exchange-item" -> new String[]{"Można wymieniać tylko zwykłe, nieuszkodzone stosy bez nazwy, zaklęć i NBT.", "Only pristine ordinary stacks without names, enchantments, damage, or NBT can be exchanged."};
            case "wallet-insufficient" -> new String[]{"Za mało żetonów w portfelu.", "Not enough chips in your wallet."};
            case "below-minimum-buyin" -> new String[]{"Pierwszy buy-in musi wynosić co najmniej 100 żetonów.", "The initial buy-in must be at least 100 chips."};
            case "bot-limit" -> new String[]{"Można mieć łącznie 1–4 boty i najwyżej 9 miejsc przy stole.", "A table may have 1–4 bots and at most 9 total seats."};
            case "no-bots" -> new String[]{"Nie masz botów do usunięcia przy tym stole.", "You have no sponsored bots to remove at this table."};
            case "buyin-too-large" -> new String[]{"Ta wpłata jest zbyt duża.", "That buy-in is too large."};
            case "no-chips" -> new String[]{"Nie masz żetonów do wypłaty.", "You have no chips to cash out."};
            default -> new String[]{"Poker: błąd " + code, "Poker error: " + code};
        };
        send(player, text[0], text[1], ChatFormatting.RED);
    }

    private static String phaseName(PokerGame.Phase phase, boolean en) {
        return switch (phase) {
            case WAITING -> en ? "waiting" : "oczekiwanie";
            case PREFLOP -> "preflop"; case FLOP -> "flop"; case TURN -> "turn"; case RIVER -> "river";
        };
    }

    private static String handName(PokerHandEvaluator.HandValue hand, boolean en) {
        return switch (hand.category()) {
            case 8 -> en ? "straight flush" : "poker"; case 7 -> en ? "four of a kind" : "kareta";
            case 6 -> en ? "full house" : "full"; case 5 -> en ? "flush" : "kolor";
            case 4 -> en ? "straight" : "strit"; case 3 -> en ? "three of a kind" : "trójka";
            case 2 -> en ? "two pair" : "dwie pary"; case 1 -> en ? "pair" : "para";
            default -> en ? "high card" : "wysoka karta";
        };
    }

}
