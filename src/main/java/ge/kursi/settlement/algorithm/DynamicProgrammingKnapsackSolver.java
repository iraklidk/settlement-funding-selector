package ge.kursi.settlement.algorithm;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;

public final class DynamicProgrammingKnapsackSolver implements KnapsackSolver {

    @Override
    public KnapsackSolution solve(List<KnapsackItem> items, long capacity) {
        if (capacity < 0) {
            throw new IllegalArgumentException("capacity must not be negative, got " + capacity);
        }
        if (items.isEmpty() || capacity == 0) {
            return KnapsackSolution.EMPTY;
        }
        if (capacity > Integer.MAX_VALUE - 8) {
            throw new IllegalArgumentException("capacity too large for the DP solver: " + capacity);
        }

        int cap = (int) capacity;
        int n = items.size();
        // bestValue[w]  = max value achievable with total weight <= w
        // bestWeight[w] = smallest total weight that achieves bestValue[w]
        long[] bestValue = new long[cap + 1];
        long[] bestWeight = new long[cap + 1];
        BitSet[] taken = new BitSet[n];

        for (int i = 0; i < n; i++) {
            KnapsackItem item = items.get(i);
            BitSet takenAt = new BitSet(cap + 1);
            taken[i] = takenAt;
            if (item.weight() > cap) {
                continue;
            }
            int weight = (int) item.weight();
            for (int w = cap; w >= weight; w--) { // heart of dp
                long candidateValue = bestValue[w - weight] + item.value();
                long candidateWeight = bestWeight[w - weight] + weight;
                if (candidateValue > bestValue[w]
                        || (candidateValue == bestValue[w] && candidateWeight < bestWeight[w])) {
                    bestValue[w] = candidateValue;
                    bestWeight[w] = candidateWeight;
                    takenAt.set(w);
                }
            }
        }

        List<Integer> selected = new ArrayList<>();
        int w = cap;
        for (int i = n - 1; i >= 0; i--) {
            if (taken[i].get(w)) {
                selected.add(items.get(i).index());
                w -= (int) items.get(i).weight();
            }
        }
        Collections.reverse(selected);
        return new KnapsackSolution(selected, bestWeight[cap], bestValue[cap]);
    }
}
