package pl.aridlin.psychiatrykroles;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PokerGameTest {
    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID C = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Test
    void enforcesTurnAndRunsAllInHandToShowdownWithoutChangingBackedTotal() {
        PokerGame game = fundedGame();
        game.start(new Random(2137));
        UUID turn = game.turnPlayer().id;
        UUID wrong = List.of(A, B, C).stream().filter(id -> !id.equals(turn)).findFirst().orElseThrow();
        PokerGame.PokerException error = assertThrows(PokerGame.PokerException.class, () -> game.call(wrong));
        assertEquals("not-your-turn", error.code);

        while (game.phase() != PokerGame.Phase.WAITING) game.allIn(game.turnPlayer().id);

        assertEquals(300, game.players().stream().mapToInt(player -> player.chips).sum());
        assertEquals(300, game.vaultValue());
        assertTrue(game.lastShowdown().stream().mapToInt(PokerGame.ShowdownEntry::payout).sum() > 0);
    }

    @Test
    void splitsMainAndSidePotsAndAwardsOddTieChipBySeatOrder() {
        Map<UUID, Integer> contributions = new LinkedHashMap<>();
        contributions.put(A, 100); contributions.put(B, 200); contributions.put(C, 300);
        Map<UUID, PokerHandEvaluator.HandValue> hands = Map.of(
            A, value(8, 14), B, value(7, 14, 13), C, value(1, 2, 14, 13, 12));
        assertEquals(Map.of(A, 300, B, 200, C, 100), PokerGame.settlePots(contributions, Set.of(), hands, List.of(A, B, C)));

        contributions = new LinkedHashMap<>();
        contributions.put(A, 100); contributions.put(B, 100); contributions.put(C, 1);
        var tie = value(4, 14);
        assertEquals(Map.of(A, 101, B, 100), PokerGame.settlePots(contributions, Set.of(C), Map.of(A, tie, B, tie), List.of(A, B, C)));
    }

    @Test
    void persistsActiveHandVaultAndDisconnectRecovery() {
        PokerGame game = fundedGame();
        game.start(new Random(69));
        UUID disconnected = game.turnPlayer().id;
        game.disconnect(disconnected);
        assertNotEquals(disconnected, game.turnPlayer() == null ? null : game.turnPlayer().id);

        CompoundTag saved = game.save();
        PokerGame loaded = PokerGame.load(saved);
        assertEquals(game.phase(), loaded.phase());
        assertEquals(game.board(), loaded.board());
        assertEquals(300, loaded.vaultValue());
        assertEquals(game.players().stream().mapToInt(player -> player.chips + player.committedHand).sum(),
            loaded.players().stream().mapToInt(player -> player.chips + player.committedHand).sum());
        assertTrue(loaded.players().stream().noneMatch(player -> player.connected));
        UUID returning = List.of(A, B, C).stream().filter(id -> !id.equals(disconnected)).findFirst().orElseThrow();
        loaded.reconnect(returning, returning.toString()); loaded.recoverDisconnectedTurns();
        assertTrue(loaded.phase() == PokerGame.Phase.WAITING || loaded.turnPlayer().connected);
    }

    @Test
    void buyInRejectsTrashAndCashOutNeverMintsItems() {
        PokerGame game = new PokerGame("vault", A); game.join(A, "A");
        assertEquals(0, PokerItemValues.value("minecraft:cobblestone"));
        assertTrue(PokerItemValues.value("minecraft:nautilus_shell") > 0);
        assertThrows(PokerGame.PokerException.class, () -> game.buyIn(A, "minecraft:cobblestone", 64, stackTag("minecraft:cobblestone")));
        game.buyIn(A, "minecraft:diamond", 1, stackTag("minecraft:diamond"));
        PokerGame.CashOut out = game.cashOut(A);
        assertEquals(100, out.paid()); assertEquals(0, out.remaining());
        assertEquals(1, out.items().stream().mapToInt(PokerGame.EscrowItem::count).sum());
        assertEquals(0, game.vaultValue());
    }

    @Test
    void operatorResetRefundsCommitmentsInsteadOfMintingOrDeletingValue() {
        PokerGame game = fundedGame(); game.start(new Random(420));
        assertTrue(game.pot() > 0);
        game.reset();
        assertEquals(PokerGame.Phase.WAITING, game.phase());
        assertEquals(300, game.players().stream().mapToInt(player -> player.chips).sum());
        assertEquals(300, game.vaultValue());
    }

    private static PokerGame fundedGame() {
        PokerGame game = new PokerGame("test", A); game.join(A, "A"); game.join(B, "B"); game.join(C, "C");
        game.buyIn(A, "minecraft:diamond", 1, stackTag("minecraft:diamond"));
        game.buyIn(B, "minecraft:diamond", 1, stackTag("minecraft:diamond"));
        game.buyIn(C, "minecraft:diamond", 1, stackTag("minecraft:diamond"));
        return game;
    }

    private static CompoundTag stackTag(String item) {
        CompoundTag tag = new CompoundTag(); tag.putString("id", item); tag.putByte("Count", (byte) 1); return tag;
    }

    private static PokerHandEvaluator.HandValue value(int category, Integer... kickers) {
        return new PokerHandEvaluator.HandValue(category, List.of(kickers));
    }
}
