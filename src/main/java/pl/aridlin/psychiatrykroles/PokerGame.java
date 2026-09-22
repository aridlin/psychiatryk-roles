package pl.aridlin.psychiatrykroles;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

final class PokerGame {
    enum Phase { WAITING, PREFLOP, FLOP, TURN, RIVER }

    static final class PlayerState {
        final UUID id;
        String name;
        int chips;
        boolean boughtIn;
        boolean connected = true;
        boolean folded;
        boolean allIn;
        boolean pendingLeave;
        int committedRound;
        int committedHand;
        final List<PokerCard> hole = new ArrayList<>(2);

        PlayerState(UUID id, String name, int chips) { this.id = id; this.name = name; this.chips = chips; }
    }

    static final class PokerException extends RuntimeException {
        final String code;
        PokerException(String code) { super(code); this.code = code; }
    }

    record ShowdownEntry(UUID player, PokerHandEvaluator.HandValue hand, int payout) {}
    record EscrowItem(String itemId, int unitValue, int count, CompoundTag stackTag) {
        EscrowItem {
            if (unitValue <= 0 || count <= 0) throw new IllegalArgumentException("Invalid escrow item");
            stackTag = stackTag.copy();
        }
        int totalValue() { return unitValue * count; }
    }
    record CashOut(List<EscrowItem> items, int paid, int remaining) {}

    private final String id;
    private final UUID owner;
    private final int minimumBuyIn;
    private final int smallBlind;
    private final int bigBlind;
    private final List<PlayerState> players = new ArrayList<>();
    private final List<PokerCard> board = new ArrayList<>(5);
    private final List<PokerCard> deck = new ArrayList<>(52);
    private final List<EscrowItem> vault = new ArrayList<>();
    private final Set<UUID> acted = new LinkedHashSet<>();
    private final Set<UUID> raiseClosedFor = new LinkedHashSet<>();
    private final List<ShowdownEntry> lastShowdown = new ArrayList<>();
    private final List<UUID> lastAutoFolded = new ArrayList<>();
    private Phase phase = Phase.WAITING;
    private int dealerIndex = -1;
    private int turnIndex = -1;
    private int deckPosition;
    private int currentBet;
    private int lastRaise;
    private long handNumber;
    private String lastResult = "";

    PokerGame(String id, UUID owner) { this(id, owner, 100, 10, 20); }

    PokerGame(String id, UUID owner, int minimumBuyIn, int smallBlind, int bigBlind) {
        this.id = id;
        this.owner = owner;
        this.minimumBuyIn = minimumBuyIn;
        this.smallBlind = smallBlind;
        this.bigBlind = bigBlind;
    }

    String id() { return id; }
    UUID owner() { return owner; }
    Phase phase() { return phase; }
    List<PlayerState> players() { return List.copyOf(players); }
    List<PokerCard> board() { return List.copyOf(board); }
    int pot() { return players.stream().mapToInt(player -> player.committedHand).sum(); }
    int currentBet() { return currentBet; }
    long handNumber() { return handNumber; }
    int minimumBuyIn() { return minimumBuyIn; }
    int vaultValue() { return vault.stream().mapToInt(EscrowItem::totalValue).sum(); }
    boolean canDelete() { return phase == Phase.WAITING && players.isEmpty() && vault.isEmpty(); }
    String lastResult() { return lastResult; }
    void clearLastResult() { lastResult = ""; lastShowdown.clear(); }
    List<ShowdownEntry> lastShowdown() { return List.copyOf(lastShowdown); }
    List<UUID> drainAutoFolded() { List<UUID> result = List.copyOf(lastAutoFolded); lastAutoFolded.clear(); return result; }
    PlayerState turnPlayer() { return turnIndex >= 0 && turnIndex < players.size() ? players.get(turnIndex) : null; }
    PlayerState player(UUID id) { return players.stream().filter(player -> player.id.equals(id)).findFirst().orElse(null); }

    void join(UUID playerId, String name) {
        PlayerState existing = player(playerId);
        if (existing != null) {
            existing.connected = true;
            existing.pendingLeave = false;
            existing.name = name;
            return;
        }
        if (phase != Phase.WAITING) throw new PokerException("hand-active");
        if (players.size() >= 9) throw new PokerException("table-full");
        players.add(new PlayerState(playerId, name, 0));
    }

    void leave(UUID playerId) {
        int index = indexOf(playerId);
        if (index < 0) throw new PokerException("not-seated");
        if (phase == Phase.WAITING) {
            if (players.get(index).chips > 0) throw new PokerException("cashout-first");
            players.remove(index);
            dealerIndex = -1;
            return;
        }
        PlayerState player = players.get(index);
        player.pendingLeave = true;
        player.connected = false;
        if (!player.folded && !player.allIn) {
            player.folded = true;
            acted.add(playerId);
            if (turnIndex == index) progress(index);
            else if (turnIndex >= 0) progress(Math.floorMod(turnIndex - 1, players.size()));
        }
    }

    void disconnect(UUID playerId) {
        PlayerState player = player(playerId);
        if (player == null) return;
        player.connected = false;
        if (phase != Phase.WAITING && turnIndex == indexOf(playerId)) {
            progress(Math.floorMod(turnIndex - 1, players.size()));
        }
    }

    void reconnect(UUID playerId, String name) {
        PlayerState player = player(playerId);
        if (player != null) { player.connected = true; player.name = name; }
    }

    void recoverDisconnectedTurns() {
        if (phase != Phase.WAITING && turnPlayer() != null && !turnPlayer().connected)
            progress(Math.floorMod(turnIndex - 1, players.size()));
    }

    int buyIn(UUID playerId, String itemId, int count, CompoundTag stackTag) {
        if (phase != Phase.WAITING) throw new PokerException("hand-active");
        PlayerState player = requirePlayer(playerId);
        int unit = PokerItemValues.value(itemId);
        if (unit <= 0 || count <= 0) throw new PokerException("worthless-item");
        int credit;
        try { credit = Math.multiplyExact(unit, count); }
        catch (ArithmeticException error) { throw new PokerException("buyin-too-large"); }
        if (player.chips > 10_000_000 - credit) throw new PokerException("buyin-too-large");
        vault.add(new EscrowItem(itemId, unit, count, stackTag));
        player.chips += credit;
        if (player.chips >= minimumBuyIn) player.boughtIn = true;
        return credit;
    }

    CashOut cashOut(UUID playerId) {
        if (phase != Phase.WAITING) throw new PokerException("hand-active");
        PlayerState player = requirePlayer(playerId);
        if (player.chips <= 0) throw new PokerException("no-chips");
        int remaining = player.chips;
        List<EscrowItem> returned = new ArrayList<>();
        List<EscrowItem> ordered = vault.stream()
            .sorted(Comparator.comparingInt(EscrowItem::unitValue).reversed()).toList();
        for (EscrowItem stack : ordered) {
            int unit = stack.unitValue();
            int count = Math.min(stack.count(), remaining / unit);
            if (count <= 0) continue;
            returned.add(new EscrowItem(stack.itemId(), unit, count, stack.stackTag()));
            removeFromVault(stack, count);
            remaining -= unit * count;
        }
        int paid = player.chips - remaining;
        player.chips = remaining;
        if (remaining == 0) player.boughtIn = false;
        return new CashOut(List.copyOf(returned), paid, remaining);
    }

    void reset() {
        phase = Phase.WAITING;
        board.clear(); deck.clear(); acted.clear(); raiseClosedFor.clear(); lastShowdown.clear(); lastAutoFolded.clear();
        deckPosition = 0; currentBet = 0; lastRaise = bigBlind; turnIndex = -1; dealerIndex = -1;
        for (PlayerState player : players) {
            player.chips += player.committedHand; player.folded = false; player.allIn = false;
            player.committedRound = 0; player.committedHand = 0; player.hole.clear();
        }
        lastResult = "reset";
    }

    void start(Random random) {
        if (phase != Phase.WAITING) throw new PokerException("hand-active");
        cleanupDeparted();
        List<Integer> eligible = eligibleSeatIndexes();
        if (eligible.size() < 2) throw new PokerException("need-two-funded");
        for (PlayerState player : players) {
            player.folded = !player.boughtIn || player.chips <= 0 || player.pendingLeave || !player.connected;
            player.allIn = false; player.committedRound = 0; player.committedHand = 0; player.hole.clear();
        }
        board.clear(); acted.clear(); raiseClosedFor.clear(); lastShowdown.clear(); lastAutoFolded.clear(); lastResult = "";
        deck.clear(); deck.addAll(PokerCard.deck()); Collections.shuffle(deck, random); deckPosition = 0;
        dealerIndex = nextEligible(dealerIndex, false);
        handNumber++;
        phase = Phase.PREFLOP;
        for (int round = 0; round < 2; round++) for (int seat : seatsAfter(dealerIndex, false)) players.get(seat).hole.add(draw());
        int smallIndex = eligible.size() == 2 ? dealerIndex : nextEligible(dealerIndex, false);
        int bigIndex = nextEligible(smallIndex, false);
        postBlind(players.get(smallIndex), smallBlind);
        postBlind(players.get(bigIndex), bigBlind);
        currentBet = Math.max(players.get(smallIndex).committedRound, players.get(bigIndex).committedRound);
        lastRaise = bigBlind;
        turnIndex = nextActionable(bigIndex);
        progress(bigIndex);
    }

    void check(UUID playerId) {
        PlayerState player = requireTurn(playerId);
        if (player.committedRound != currentBet) throw new PokerException("cannot-check");
        acted.add(playerId);
        progress(indexOf(playerId));
    }

    void call(UUID playerId) {
        PlayerState player = requireTurn(playerId);
        int amount = Math.min(player.chips, currentBet - player.committedRound);
        if (amount <= 0) throw new PokerException("nothing-to-call");
        commit(player, amount);
        acted.add(playerId);
        progress(indexOf(playerId));
    }

    void raiseTo(UUID playerId, int target) {
        PlayerState player = requireTurn(playerId);
        int maximum = player.committedRound + player.chips;
        if (target <= currentBet || target > maximum) throw new PokerException("invalid-raise");
        if (raiseClosedFor.contains(playerId)) throw new PokerException("raise-not-reopened");
        boolean allInRaise = target == maximum;
        int raiseSize = target - currentBet;
        if (!allInRaise && raiseSize < lastRaise) throw new PokerException("raise-too-small");
        Set<UUID> previouslyActed = new LinkedHashSet<>(acted);
        commit(player, target - player.committedRound);
        currentBet = target;
        acted.clear();
        if (raiseSize >= lastRaise) { lastRaise = raiseSize; raiseClosedFor.clear(); }
        else raiseClosedFor.addAll(previouslyActed);
        acted.add(playerId);
        progress(indexOf(playerId));
    }

    void allIn(UUID playerId) {
        PlayerState player = requireTurn(playerId);
        if (player.chips <= 0) throw new PokerException("already-all-in");
        int target = player.committedRound + player.chips;
        int previousBet = currentBet;
        if (target > previousBet && raiseClosedFor.contains(playerId)) throw new PokerException("raise-not-reopened");
        Set<UUID> previouslyActed = new LinkedHashSet<>(acted);
        commit(player, player.chips);
        if (target > previousBet) {
            int raiseSize = target - previousBet;
            currentBet = target;
            acted.clear();
            if (raiseSize >= lastRaise) { lastRaise = raiseSize; raiseClosedFor.clear(); }
            else raiseClosedFor.addAll(previouslyActed);
        }
        acted.add(playerId);
        progress(indexOf(playerId));
    }

    void fold(UUID playerId) {
        PlayerState player = requireTurn(playerId);
        player.folded = true;
        acted.add(playerId);
        progress(indexOf(playerId));
    }

    private void progress(int afterIndex) {
        int guard = 0;
        while (phase != Phase.WAITING && guard++ < 100) {
            List<PlayerState> contenders = players.stream().filter(player -> !player.folded && player.committedHand >= 0).toList();
            if (contenders.size() == 1) { awardUncontested(contenders.get(0)); return; }
            if (roundComplete()) { advanceStreet(); afterIndex = dealerIndex; continue; }
            int next = nextActionable(afterIndex);
            if (next < 0) { advanceStreet(); afterIndex = dealerIndex; continue; }
            turnIndex = next;
            PlayerState player = players.get(next);
            if (player.connected) return;
            if (player.committedRound == currentBet) {
                acted.add(player.id);
            } else {
                player.folded = true;
                acted.add(player.id);
                lastAutoFolded.add(player.id);
            }
            afterIndex = next;
        }
        if (guard >= 100) throw new IllegalStateException("Poker progress loop did not settle");
    }

    private boolean roundComplete() {
        List<PlayerState> actionable = players.stream().filter(player -> !player.folded && !player.allIn).toList();
        if (actionable.isEmpty()) return true;
        if (actionable.size() == 1) return actionable.get(0).committedRound == currentBet;
        return actionable.stream().allMatch(player -> acted.contains(player.id) && player.committedRound == currentBet);
    }

    private void advanceStreet() {
        for (PlayerState player : players) player.committedRound = 0;
        acted.clear(); raiseClosedFor.clear(); currentBet = 0; lastRaise = bigBlind;
        switch (phase) {
            case PREFLOP -> { burn(); board.add(draw()); board.add(draw()); board.add(draw()); phase = Phase.FLOP; }
            case FLOP -> { burn(); board.add(draw()); phase = Phase.TURN; }
            case TURN -> { burn(); board.add(draw()); phase = Phase.RIVER; }
            case RIVER -> { showdown(); return; }
            default -> throw new IllegalStateException("Cannot advance " + phase);
        }
        turnIndex = nextActionable(dealerIndex);
    }

    private void showdown() {
        Map<UUID, Integer> contributions = new LinkedHashMap<>();
        Set<UUID> folded = new HashSet<>();
        Map<UUID, PokerHandEvaluator.HandValue> hands = new HashMap<>();
        List<UUID> order = seatsAfter(dealerIndex, true).stream().map(index -> players.get(index).id).toList();
        for (PlayerState player : players) {
            if (player.committedHand <= 0) continue;
            contributions.put(player.id, player.committedHand);
            if (player.folded) folded.add(player.id);
            else {
                List<PokerCard> seven = new ArrayList<>(board); seven.addAll(player.hole);
                hands.put(player.id, PokerHandEvaluator.evaluate(seven));
            }
        }
        Map<UUID, Integer> payouts = settlePots(contributions, folded, hands, order);
        lastShowdown.clear();
        hands.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry ->
            lastShowdown.add(new ShowdownEntry(entry.getKey(), entry.getValue(), payouts.getOrDefault(entry.getKey(), 0))));
        payouts.forEach((id, amount) -> requirePlayer(id).chips += amount);
        lastResult = "showdown";
        finishHand();
    }

    static Map<UUID, Integer> settlePots(Map<UUID, Integer> contributions, Set<UUID> folded,
                                         Map<UUID, PokerHandEvaluator.HandValue> hands, List<UUID> order) {
        Map<UUID, Integer> payouts = new LinkedHashMap<>();
        List<Integer> levels = contributions.values().stream().filter(value -> value > 0).distinct().sorted().toList();
        int previous = 0;
        for (int level : levels) {
            List<UUID> contributors = contributions.entrySet().stream().filter(entry -> entry.getValue() >= level)
                .map(Map.Entry::getKey).toList();
            int amount = (level - previous) * contributors.size();
            previous = level;
            List<UUID> eligible = contributors.stream().filter(id -> !folded.contains(id) && hands.containsKey(id)).toList();
            if (eligible.isEmpty()) continue;
            PokerHandEvaluator.HandValue best = eligible.stream().map(hands::get).max(Comparator.naturalOrder()).orElseThrow();
            List<UUID> winners = order.stream().filter(eligible::contains).filter(id -> hands.get(id).compareTo(best) == 0).toList();
            int share = amount / winners.size(), remainder = amount % winners.size();
            for (int index = 0; index < winners.size(); index++) payouts.merge(winners.get(index), share + (index < remainder ? 1 : 0), Integer::sum);
        }
        return payouts;
    }

    private void awardUncontested(PlayerState winner) {
        int amount = pot(); winner.chips += amount; lastShowdown.clear();
        lastShowdown.add(new ShowdownEntry(winner.id, null, amount));
        lastResult = "uncontested";
        finishHand();
    }

    private void finishHand() {
        phase = Phase.WAITING; turnIndex = -1; currentBet = 0; acted.clear(); raiseClosedFor.clear();
        for (PlayerState player : players) {
            player.committedRound = 0; player.committedHand = 0; player.folded = false; player.allIn = false; player.hole.clear();
        }
        cleanupDeparted();
    }

    private void cleanupDeparted() {
        UUID dealer = dealerIndex >= 0 && dealerIndex < players.size() ? players.get(dealerIndex).id : null;
        players.removeIf(player -> player.pendingLeave && player.chips == 0 && player.committedHand == 0);
        dealerIndex = dealer == null ? -1 : indexOf(dealer);
    }

    private void postBlind(PlayerState player, int amount) { commit(player, Math.min(amount, player.chips)); }
    private void commit(PlayerState player, int amount) {
        if (amount < 0 || amount > player.chips) throw new IllegalArgumentException("Invalid commitment");
        player.chips -= amount; player.committedRound += amount; player.committedHand += amount;
        if (player.chips == 0) player.allIn = true;
    }

    private PokerCard draw() { if (deckPosition >= deck.size()) throw new IllegalStateException("Deck exhausted"); return deck.get(deckPosition++); }
    private void burn() { draw(); }
    private PlayerState requirePlayer(UUID id) { PlayerState player = player(id); if (player == null) throw new PokerException("not-seated"); return player; }
    private PlayerState requireTurn(UUID id) {
        if (phase == Phase.WAITING) throw new PokerException("no-hand");
        PlayerState player = requirePlayer(id);
        if (turnPlayer() != player) throw new PokerException("not-your-turn");
        if (player.folded || player.allIn) throw new PokerException("cannot-act");
        return player;
    }
    private int indexOf(UUID id) { for (int i = 0; i < players.size(); i++) if (players.get(i).id.equals(id)) return i; return -1; }
    private List<Integer> eligibleSeatIndexes() {
        List<Integer> result = new ArrayList<>(); for (int i = 0; i < players.size(); i++) if (players.get(i).boughtIn && players.get(i).chips > 0 && playerIsAvailable(players.get(i))) result.add(i); return result;
    }
    private boolean playerIsAvailable(PlayerState player) { return !player.pendingLeave && player.connected; }
    private int nextEligible(int after, boolean includeFolded) {
        if (players.isEmpty()) return -1;
        for (int step = 1; step <= players.size(); step++) {
            int index = Math.floorMod(after + step, players.size()); PlayerState player = players.get(index);
            if (player.chips + player.committedHand > 0 && !player.pendingLeave && (includeFolded || !player.folded)) return index;
        }
        return -1;
    }
    private int nextActionable(int after) {
        if (players.isEmpty()) return -1;
        for (int step = 1; step <= players.size(); step++) {
            int index = Math.floorMod(after + step, players.size()); PlayerState player = players.get(index);
            if (!player.folded && !player.allIn && !player.pendingLeave) return index;
        }
        return -1;
    }
    private List<Integer> seatsAfter(int after, boolean includeFolded) {
        List<Integer> result = new ArrayList<>();
        if (players.isEmpty()) return result;
        for (int step = 1; step <= players.size(); step++) {
            int index = Math.floorMod(after + step, players.size()); PlayerState player = players.get(index);
            if (!player.pendingLeave && (includeFolded || (!player.folded && player.chips + player.committedHand > 0))) result.add(index);
        }
        return result;
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Id", id); tag.putUUID("Owner", owner); tag.putInt("MinimumBuyIn", minimumBuyIn);
        tag.putInt("SmallBlind", smallBlind); tag.putInt("BigBlind", bigBlind); tag.putString("Phase", phase.name());
        tag.putInt("Dealer", dealerIndex); tag.putInt("Turn", turnIndex); tag.putInt("DeckPosition", deckPosition);
        tag.putInt("CurrentBet", currentBet); tag.putInt("LastRaise", lastRaise); tag.putLong("HandNumber", handNumber);
        tag.putString("LastResult", lastResult);
        tag.putIntArray("Deck", deck.stream().mapToInt(PokerCard::id).toArray());
        tag.putIntArray("Board", board.stream().mapToInt(PokerCard::id).toArray());
        ListTag vaultTags = new ListTag();
        vault.forEach(stack -> { CompoundTag value = new CompoundTag(); value.putString("Item", stack.itemId());
            value.putInt("Value", stack.unitValue()); value.putInt("Count", stack.count()); value.put("Stack", stack.stackTag().copy()); vaultTags.add(value); });
        tag.put("Vault", vaultTags);
        ListTag playerTags = new ListTag();
        for (PlayerState player : players) {
            CompoundTag value = new CompoundTag(); value.putUUID("Id", player.id); value.putString("Name", player.name);
            value.putInt("Chips", player.chips); value.putBoolean("BoughtIn", player.boughtIn); value.putBoolean("Connected", player.connected); value.putBoolean("Folded", player.folded);
            value.putBoolean("AllIn", player.allIn); value.putBoolean("PendingLeave", player.pendingLeave);
            value.putInt("CommittedRound", player.committedRound); value.putInt("CommittedHand", player.committedHand);
            value.putIntArray("Hole", player.hole.stream().mapToInt(PokerCard::id).toArray()); playerTags.add(value);
        }
        tag.put("Players", playerTags);
        ListTag actedTags = new ListTag(); acted.forEach(id -> { CompoundTag value = new CompoundTag(); value.putUUID("Id", id); actedTags.add(value); }); tag.put("Acted", actedTags);
        ListTag closedTags = new ListTag(); raiseClosedFor.forEach(id -> { CompoundTag value = new CompoundTag(); value.putUUID("Id", id); closedTags.add(value); }); tag.put("RaiseClosed", closedTags);
        return tag;
    }

    static PokerGame load(CompoundTag tag) {
        int minimum = tag.contains("MinimumBuyIn") ? tag.getInt("MinimumBuyIn") : 100;
        PokerGame game = new PokerGame(tag.getString("Id"), tag.getUUID("Owner"), minimum, tag.getInt("SmallBlind"), tag.getInt("BigBlind"));
        try { game.phase = Phase.valueOf(tag.getString("Phase")); } catch (IllegalArgumentException ignored) { game.phase = Phase.WAITING; }
        game.dealerIndex = tag.getInt("Dealer"); game.turnIndex = tag.getInt("Turn"); game.deckPosition = tag.getInt("DeckPosition");
        game.currentBet = tag.getInt("CurrentBet"); game.lastRaise = tag.getInt("LastRaise"); game.handNumber = tag.getLong("HandNumber"); game.lastResult = tag.getString("LastResult");
        for (int id : tag.getIntArray("Deck")) game.deck.add(PokerCard.fromId(id));
        for (int id : tag.getIntArray("Board")) game.board.add(PokerCard.fromId(id));
        for (Tag raw : tag.getList("Vault", Tag.TAG_COMPOUND)) {
            CompoundTag value = (CompoundTag) raw; int canonical = PokerItemValues.value(value.getString("Item"));
            if (canonical > 0 && value.getInt("Value") == canonical && value.getInt("Count") > 0
                && value.getString("Item").equals(value.getCompound("Stack").getString("id")))
                game.vault.add(new EscrowItem(value.getString("Item"), canonical, value.getInt("Count"), value.getCompound("Stack")));
        }
        for (Tag raw : tag.getList("Players", Tag.TAG_COMPOUND)) {
            CompoundTag value = (CompoundTag) raw; PlayerState player = new PlayerState(value.getUUID("Id"), value.getString("Name"), value.getInt("Chips"));
            player.boughtIn = value.getBoolean("BoughtIn"); player.connected = false; player.folded = value.getBoolean("Folded"); player.allIn = value.getBoolean("AllIn");
            player.pendingLeave = value.getBoolean("PendingLeave"); player.committedRound = value.getInt("CommittedRound"); player.committedHand = value.getInt("CommittedHand");
            for (int id : value.getIntArray("Hole")) player.hole.add(PokerCard.fromId(id)); game.players.add(player);
        }
        for (Tag raw : tag.getList("Acted", Tag.TAG_COMPOUND)) game.acted.add(((CompoundTag) raw).getUUID("Id"));
        for (Tag raw : tag.getList("RaiseClosed", Tag.TAG_COMPOUND)) game.raiseClosedFor.add(((CompoundTag) raw).getUUID("Id"));
        if (game.phase != Phase.WAITING && (game.deck.size() != 52 || game.turnIndex < 0 || game.turnIndex >= game.players.size())) {
            game.reset(); game.lastResult = "recovered-reset";
        }
        return game;
    }

    private void removeFromVault(EscrowItem removed, int count) {
        int index = vault.indexOf(removed);
        if (index < 0 || count > removed.count()) throw new IllegalStateException("Poker vault underflow");
        vault.remove(index);
        if (count < removed.count()) vault.add(index, new EscrowItem(removed.itemId(), removed.unitValue(), removed.count() - count, removed.stackTag()));
    }
}
