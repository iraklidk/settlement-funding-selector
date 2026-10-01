package ge.kursi.settlement.algorithm;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class BranchAndBoundKnapsackSolver implements KnapsackSolver {

    @Override
    public KnapsackSolution solve(List<KnapsackItem> items, long capacity) {
        if (capacity < 0) {
            throw new IllegalArgumentException("capacity must not be negative, got " + capacity);
        }
        List<KnapsackItem> fitting = new ArrayList<>();
        for (KnapsackItem item : items) {
            if (item.weight() <= capacity) {
                fitting.add(item);
            }
        }
        if (fitting.isEmpty()) {
            return KnapsackSolution.EMPTY;
        }
        // Highest value-per-weight first; the sort is stable so input order breaks density ties.
        fitting.sort(Comparator.comparingDouble((KnapsackItem it) -> (double) it.value() / it.weight()).reversed());

        Search search = new Search(fitting, capacity);
        search.explore(0, 0, 0);

        List<Integer> selected = new ArrayList<>();
        for (int i = 0; i < fitting.size(); i++) {
            if (search.bestTaken[i]) {
                selected.add(fitting.get(i).index());
            }
        }
        selected.sort(null);
        return new KnapsackSolution(selected, search.bestWeight, search.bestValue);
    }

    private static final class Search {
        private final List<KnapsackItem> items;
        private final long capacity;
        private final boolean[] currentTaken;
        private final boolean[] bestTaken;
        private long bestValue = 0;
        private long bestWeight = 0;

        Search(List<KnapsackItem> items, long capacity) {
            this.items = items;
            this.capacity = capacity;
            this.currentTaken = new boolean[items.size()];
            this.bestTaken = new boolean[items.size()];
        }

        void explore(int depth, long weight, long value) {
            if (value > bestValue || (value == bestValue && weight < bestWeight)) {
                bestValue = value;
                bestWeight = weight;
                System.arraycopy(currentTaken, 0, bestTaken, 0, currentTaken.length);
            }
            if (depth == items.size()) {
                return;
            }
            KnapsackItem item = items.get(depth);
            if (weight + item.weight() <= capacity) {
                currentTaken[depth] = true;
                explore(depth + 1, weight + item.weight(), value + item.value());
                currentTaken[depth] = false; // BACKTRACK
            }
            explore(depth + 1, weight, value);
        }

//        /** Fractional-knapsack upper bound: can the remaining items still beat the incumbent? */
//        private boolean canImprove(int depth, long weight, long value) {
//            long remaining = capacity - weight;
//            long bound = value;
//            for (int i = depth; i < items.size(); i++) {
//                KnapsackItem item = items.get(i);
//                if (item.weight() <= remaining) {
//                    remaining -= item.weight();
//                    bound += item.value();
//                } else {
//                    // +1 absorbs floating-point rounding so the bound stays a true upper bound.
//                    bound += (long) Math.ceil((double) remaining * item.value() / item.weight()) + 1;
//                    break;
//                }
//            }
//            if (bound < bestValue) {
//                return false;
//            }
//            // Equal value can only win by being lighter, and weight never decreases down the tree.
//            return bound > bestValue || weight < bestWeight;
//        }
    }
}
