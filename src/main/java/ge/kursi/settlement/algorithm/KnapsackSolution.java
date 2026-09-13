package ge.kursi.settlement.algorithm;

import java.util.List;

/**
 * Result of a knapsack solver.
 *
 * @param selectedIndexes {@link KnapsackItem#index()} of the chosen items, in ascending order
 * @param totalWeight     sum of the chosen items' weights
 * @param totalValue      sum of the chosen items' values
 */
public record KnapsackSolution(List<Integer> selectedIndexes, long totalWeight, long totalValue) {

    public static final KnapsackSolution EMPTY = new KnapsackSolution(List.of(), 0, 0);

    public KnapsackSolution {
        selectedIndexes = List.copyOf(selectedIndexes);
    }
}
