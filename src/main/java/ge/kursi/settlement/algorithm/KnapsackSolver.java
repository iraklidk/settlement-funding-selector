package ge.kursi.settlement.algorithm;

import java.util.List;

/**
 * Exact solver for the 0/1 knapsack problem.
 * <p>
 * Contract shared by all implementations:
 * <ul>
 *   <li>the returned selection maximises total value subject to {@code totalWeight <= capacity};</li>
 *   <li>among selections with the maximal value, the one with the smallest total weight is returned
 *       (leaves the most balance untouched);</li>
 *   <li>remaining ties are resolved deterministically, preferring items that appear earlier in the input.</li>
 * </ul>
 */
public interface KnapsackSolver {

    KnapsackSolution solve(List<KnapsackItem> items, long capacity);
}
