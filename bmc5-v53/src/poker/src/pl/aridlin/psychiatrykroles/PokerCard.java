package pl.aridlin.psychiatrykroles;

import java.util.ArrayList;
import java.util.List;

record PokerCard(int rank, Suit suit) {
    enum Suit { CLUBS, DIAMONDS, HEARTS, SPADES }

    PokerCard {
        if (rank < 2 || rank > 14 || suit == null) throw new IllegalArgumentException("Invalid card");
    }

    int id() {
        return suit.ordinal() * 13 + rank - 2;
    }

    static PokerCard fromId(int id) {
        if (id < 0 || id >= 52) throw new IllegalArgumentException("Invalid card id");
        return new PokerCard(id % 13 + 2, Suit.values()[id / 13]);
    }

    static List<PokerCard> deck() {
        List<PokerCard> cards = new ArrayList<>(52);
        for (Suit suit : Suit.values()) for (int rank = 2; rank <= 14; rank++) cards.add(new PokerCard(rank, suit));
        return cards;
    }

    String shortName() {
        String value = switch (rank) { case 14 -> "A"; case 13 -> "K"; case 12 -> "Q"; case 11 -> "J"; case 10 -> "10"; default -> Integer.toString(rank); };
        String symbol = switch (suit) { case CLUBS -> "♣"; case DIAMONDS -> "♦"; case HEARTS -> "♥"; case SPADES -> "♠"; };
        return value + symbol;
    }
}
