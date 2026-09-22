package pl.aridlin.psychiatrykroles;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PokerHandEvaluatorTest {
    @Test
    void ranksEveryTexasHoldemCategoryInOrder() {
        List<List<PokerCard>> hands = List.of(
            cards("2c 5d 7h 9s Jc"),
            cards("2c 2d 7h 9s Jc"),
            cards("2c 2d 7h 7s Jc"),
            cards("2c 2d 2h 9s Jc"),
            cards("2c 3d 4h 5s 6c"),
            cards("2h 5h 7h 9h Jh"),
            cards("2c 2d 2h 9s 9c"),
            cards("2c 2d 2h 2s Jc"),
            cards("6h 7h 8h 9h Th")
        );
        for (int category = 0; category < hands.size(); category++) {
            assertEquals(category, PokerHandEvaluator.evaluate(hands.get(category)).category());
        }
    }

    @Test
    void wheelIsLowerThanSixHighStraightAndBoardCanTiePlayers() {
        var wheel = PokerHandEvaluator.evaluate(cards("Ac 2d 3h 4s 5c 9d Kh"));
        var sixHigh = PokerHandEvaluator.evaluate(cards("2c 3d 4h 5s 6c 9d Kh"));
        assertTrue(sixHigh.compareTo(wheel) > 0);

        var first = PokerHandEvaluator.evaluate(cards("Ah Kh Qh Jh Th 2c 3d"));
        var second = PokerHandEvaluator.evaluate(cards("Ah Kh Qh Jh Th 9c 9d"));
        assertEquals(0, first.compareTo(second));
    }

    private static List<PokerCard> cards(String text) {
        return List.of(text.split(" ")).stream().map(PokerHandEvaluatorTest::card).toList();
    }

    private static PokerCard card(String text) {
        String ranks = "--23456789TJQKA";
        int rank = ranks.indexOf(text.charAt(0));
        PokerCard.Suit suit = switch (text.charAt(1)) {
            case 'c' -> PokerCard.Suit.CLUBS; case 'd' -> PokerCard.Suit.DIAMONDS;
            case 'h' -> PokerCard.Suit.HEARTS; case 's' -> PokerCard.Suit.SPADES;
            default -> throw new IllegalArgumentException(text);
        };
        return new PokerCard(rank, suit);
    }
}
