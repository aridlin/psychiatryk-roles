package pl.aridlin.psychiatrykroles;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
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
            .then(Commands.literal("buyin")
                .executes(context -> buyIn(context.getSource().getPlayerOrException(), -1))
                .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                    .executes(context -> buyIn(context.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(context, "count")))))
            .then(Commands.literal("cashout").executes(context -> cashOut(context.getSource().getPlayerOrException())))
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
            "/poker buyin [count] | cashout | values | start | status | cards",
            "/poker check | call | raise <total bet> | fold | allin",
            "Hold an accepted item and use buyin. Minimum 100 chips; blinds 10/20. Cobblestone and other bulk trash are worth 0.",
            "Items move into a persistent table vault. Cashout returns only real escrowed items; denomination leftovers remain as chips.",
            "Two to nine players. The table owner starts each hand. Disconnected turns auto-check when legal, otherwise auto-fold."
        } : new String[] {
            "/poker list | create <stół> | join <stół> | leave",
            "/poker buyin [liczba] | cashout | values | start | status | cards",
            "/poker check | call | raise <łączny zakład> | fold | allin",
            "Trzymaj uznawany przedmiot i użyj buyin. Minimum 100 żetonów; ciemne 10/20. Bruk i inne śmieci masowe mają wartość 0.",
            "Przedmioty trafiają do trwałego sejfu stołu. Cashout oddaje wyłącznie prawdziwy depozyt; reszta nominałowa zostaje w żetonach.",
            "Od 2 do 9 graczy. Właściciel stołu rozpoczyna rozdanie. Rozłączenie automatycznie czeka, jeśli wolno, albo pasuje."
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

    private static int buyIn(ServerPlayer player, int requestedCount) {
        PokerData data = PokerData.get(player.getServer()); PokerGame game = data.tableFor(player.getUUID());
        if (game == null) { sendError(player, "not-seated"); return 0; }
        ItemStack held = player.getMainHandItem();
        int count = requestedCount < 0 ? held.getCount() : requestedCount;
        if (held.isEmpty() || count > held.getCount()) { sendError(player, "hold-buyin-item"); return 0; }
        if (!PsychiatrykRoles.isPokerDepositAllowed(held)) { sendError(player, "protected-item"); return 0; }
        ItemStack deposited = held.copy(); deposited.setCount(count);
        try {
            int credit = game.buyIn(player.getUUID(), BuiltInRegistries.ITEM.getKey(deposited.getItem()).toString(), count,
                deposited.save(new net.minecraft.nbt.CompoundTag()));
            held.shrink(count); data.changed();
            send(player, "Wpłacono " + count + "× " + heldName(deposited) + " za " + credit + " żetonów. Saldo: " + game.player(player.getUUID()).chips + ".",
                "Deposited " + count + "× " + heldName(deposited) + " for " + credit + " chips. Balance: " + game.player(player.getUUID()).chips + ".", ChatFormatting.GREEN);
            PsychiatrykRoles.pokerAudit(player, "POKER_BUYIN", game.id() + " item=" + BuiltInRegistries.ITEM.getKey(deposited.getItem()) + " count=" + count + " credit=" + credit);
            return credit;
        } catch (PokerGame.PokerException error) { sendError(player, error.code); return 0; }
    }

    private static int cashOut(ServerPlayer player) {
        PokerData data = PokerData.get(player.getServer()); PokerGame game = data.tableFor(player.getUUID());
        if (game == null) { sendError(player, "not-seated"); return 0; }
        try {
            PokerGame.CashOut result = game.cashOut(player.getUUID()); data.changed();
            for (PokerGame.EscrowItem returned : result.items()) {
                ItemStack delivery = ItemStack.of(returned.stackTag()); delivery.setCount(returned.count());
                if (!player.getInventory().add(delivery) && !delivery.isEmpty()) player.drop(delivery, false);
            }
            send(player, "Wypłacono przedmioty warte " + result.paid() + ". Pozostałe żetony: " + result.remaining() + ".",
                "Returned escrow items worth " + result.paid() + ". Remaining chips: " + result.remaining() + ".", ChatFormatting.GREEN);
            if (result.remaining() > 0) send(player,
                "Brakuje odpowiedniego nominału w sejfie. Saldo jest zachowane; spróbuj ponownie po kolejnej wpłacie.",
                "The vault lacks a suitable denomination. Your balance is preserved; try again after another deposit.", ChatFormatting.YELLOW);
            PsychiatrykRoles.pokerAudit(player, "POKER_CASHOUT", game.id() + " paid=" + result.paid() + " remaining=" + result.remaining());
            return result.paid();
        } catch (PokerGame.PokerException error) { sendError(player, error.code); return 0; }
    }

    private static int values(ServerPlayer player) {
        boolean en = english(player);
        player.sendSystemMessage(Component.literal(en ? "POKER ITEM VALUES (per item)" : "WARTOŚCI POKEROWE (za sztukę)").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        PokerItemValues.all().entrySet().stream().sorted((a, b) -> Integer.compare(b.getValue(), a.getValue())).forEach(entry ->
            player.sendSystemMessage(Component.literal(entry.getKey() + " = " + entry.getValue()).withStyle(ChatFormatting.GRAY)));
        send(player, "Minimalna pierwsza wpłata: 100. Przedmiotów spoza listy nie można wpłacać.",
            "Minimum initial buy-in: 100. Unlisted items cannot be deposited.", ChatFormatting.YELLOW);
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
        if (!game.canDelete()) { source.sendFailure(Component.literal("Table still has players, balances, or escrow; cash out and leave first.")); return 0; }
        PokerData.get(source.getServer()).remove(id);
        source.sendSuccess(() -> Component.literal("Deleted poker table " + id).withStyle(ChatFormatting.RED), true);
        PsychiatrykRoles.pokerAudit(source.getServer(), source.getTextName(), "POKER_DELETE", id); return 1;
    }

    private static void publishAfterMutation(PokerGame game, MinecraftServer server) {
        publishAutomaticFolds(game, server);
        if (game.phase() == PokerGame.Phase.WAITING && !game.lastResult().isEmpty()) {
            publishResult(game, server); game.clearLastResult();
        }
        broadcastStatus(game, server);
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
        player.sendSystemMessage(Component.literal((en ? "Board: " : "Stół: ") + cards(game.board())).withStyle(ChatFormatting.GREEN));
        for (PokerGame.PlayerState state : game.players()) {
            String flags = (state.folded ? (en ? " folded" : " pas") : "") + (state.allIn ? " ALL-IN" : "") + (!state.connected ? (en ? " offline" : " offline") : "");
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
            case "hold-buyin-item" -> new String[]{"Trzymaj wpłacany przedmiot w głównej ręce (i podaj prawidłową liczbę).", "Hold the deposit item in your main hand (and use a valid count)."};
            case "worthless-item" -> new String[]{"Ten przedmiot nie ma wartości pokerowej. Sprawdź /poker values.", "That item has no poker value. See /poker values."};
            case "protected-item" -> new String[]{"Przedmiotów konsultanta ani przedmiotów zabezpieczonych nie można wpłacać.", "Consultant or protected items cannot be deposited."};
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

    private static String heldName(ItemStack stack) { return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(); }
}
