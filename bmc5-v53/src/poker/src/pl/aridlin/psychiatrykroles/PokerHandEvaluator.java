package pl.aridlin.psychiatrykroles;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class PokerHandEvaluator {
    record HandValue(int category, List<Integer> kickers) implements Comparable<HandValue> {
        @Override public int compareTo(HandValue other) {
            int categoryResult = Integer.compare(category, other.category);
            if (categoryResult != 0) return categoryResult;
            for (int index = 0; index < Math.min(kickers.size(), other.kickers.size()); index++) {
                int result = Integer.compare(kickers.get(index), other.kickers.get(index));
                if (result != 0) return result;
            }
            return Integer.compare(kickers.size(), other.kickers.size());
        }

        String key() { return category + ":" + kickers; }
    }

    private PokerHandEvaluator() {}

    static HandValue evaluate(List<PokerCard> cards) {
        if (cards.size() < 5 || cards.size() > 7) throw new IllegalArgumentException("Need five to seven cards");
        HandValue best = null;
        int size = cards.size();
        for (int a = 0; a < size - 4; a++) for (int b = a + 1; b < size - 3; b++)
            for (int c = b + 1; c < size - 2; c++) for (int d = c + 1; d < size - 1; d++)
                for (int e = d + 1; e < size; e++) {
                    HandValue value = evaluateFive(List.of(cards.get(a), cards.get(b), cards.get(c), cards.get(d), cards.get(e)));
                    if (best == null || value.compareTo(best) > 0) best = value;
                }
        return best;
    }

    private static HandValue evaluateFive(List<PokerCard> cards) {
        Map<Integer, Integer> counts = new HashMap<>();
        for (PokerCard card : cards) counts.merge(card.rank(), 1, Integer::sum);
        List<Integer> ranks = cards.stream().map(PokerCard::rank).distinct().sorted(Comparator.reverseOrder()).toList();
        boolean flush = cards.stream().map(PokerCard::suit).distinct().count() == 1;
        int straightHigh = straightHigh(ranks);
        if (flush && straightHigh > 0) return value(8, straightHigh);

        List<Integer> four = ranksWithCount(counts, 4);
        if (!four.isEmpty()) return value(7, four.get(0), highestExcept(ranks, four.get(0)));
        List<Integer> three = ranksWithCount(counts, 3);
        List<Integer> pairs = ranksWithCount(counts, 2);
        if (!three.isEmpty() && (!pairs.isEmpty() || three.size() > 1)) {
            int pair = !pairs.isEmpty() ? pairs.get(0) : three.get(1);
            return value(6, three.get(0), pair);
        }
        if (flush) return new HandValue(5, ranks);
        if (straightHigh > 0) return value(4, straightHigh);
        if (!three.isEmpty()) return new HandValue(3, concat(List.of(three.get(0)), except(ranks, three.get(0), 2)));
        if (pairs.size() >= 2) {
            int high = pairs.get(0), low = pairs.get(1);
            return value(2, high, low, ranks.stream().filter(rank -> rank != high && rank != low).findFirst().orElseThrow());
        }
        if (pairs.size() == 1) return new HandValue(1, concat(List.of(pairs.get(0)), except(ranks, pairs.get(0), 3)));
        return new HandValue(0, ranks);
    }

    private static int straightHigh(List<Integer> descendingDistinct) {
        List<Integer> ranks = new ArrayList<>(descendingDistinct);
        if (ranks.contains(14)) ranks.add(1);
        int run = 1;
        for (int index = 1; index < ranks.size(); index++) {
            if (ranks.get(index - 1) - 1 == ranks.get(index)) run++; else run = 1;
            if (run >= 5) return ranks.get(index - 4);
        }
        return 0;
    }

    private static List<Integer> ranksWithCount(Map<Integer, Integer> counts, int wanted) {
        return counts.entrySet().stream().filter(entry -> entry.getValue() == wanted).map(Map.Entry::getKey)
            .sorted(Comparator.reverseOrder()).toList();
    }

    private static int highestExcept(List<Integer> ranks, int excluded) {
        return ranks.stream().filter(rank -> rank != excluded).findFirst().orElseThrow();
    }

    private static List<Integer> except(List<Integer> ranks, int excluded, int limit) {
        return ranks.stream().filter(rank -> rank != excluded).limit(limit).toList();
    }

    private static List<Integer> concat(List<Integer> first, List<Integer> second) {
        List<Integer> result = new ArrayList<>(first); result.addAll(second); return result;
    }

    private static HandValue value(int category, Integer... kickers) {
        return new HandValue(category, Arrays.asList(kickers));
    }
}
