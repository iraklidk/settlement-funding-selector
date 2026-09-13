package ge.kursi.settlement.algorithm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Both solvers must honour the same {@link KnapsackSolver} contract, so every case runs against each.
 */
class KnapsackSolverContractTest {

    static Stream<Arguments> solvers() {
        return Stream.of(
                Arguments.of("dp", new DynamicProgrammingKnapsackSolver()),
                Arguments.of("branch-and-bound", new BranchAndBoundKnapsackSolver()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("solvers")
    void solvesAssignmentExample(String name, KnapsackSolver solver) {
        List<KnapsackItem> items = List.of(
                new KnapsackItem(0, 7, 150),
                new KnapsackItem(1, 9, 210),
                new KnapsackItem(2, 4, 90),
                new KnapsackItem(3, 6, 130));

        KnapsackSolution solution = solver.solve(items, 20);

        assertThat(solution.selectedIndexes()).containsExactly(0, 1, 2);
        assertThat(solution.totalWeight()).isEqualTo(20);
        assertThat(solution.totalValue()).isEqualTo(450);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("solvers")
    void returnsEmptyWhenNothingFits(String name, KnapsackSolver solver) {
        List<KnapsackItem> items = List.of(new KnapsackItem(0, 50, 10), new KnapsackItem(1, 60, 20));

        assertThat(solver.solve(items, 40)).isEqualTo(KnapsackSolution.EMPTY);
        assertThat(solver.solve(items, 0)).isEqualTo(KnapsackSolution.EMPTY);
        assertThat(solver.solve(List.of(), 40)).isEqualTo(KnapsackSolution.EMPTY);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("solvers")
    void rejectsNegativeCapacity(String name, KnapsackSolver solver) {
        assertThatThrownBy(() -> solver.solve(List.of(new KnapsackItem(0, 1, 1)), -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("solvers")
    void prefersHigherTotalValueOverGreedyDensity(String name, KnapsackSolver solver) {
        // Greedy by density would take item 0 (ratio 6) and stop; optimum is items 1 + 2.
        List<KnapsackItem> items = List.of(
                new KnapsackItem(0, 1, 6),
                new KnapsackItem(1, 5, 20),
                new KnapsackItem(2, 5, 20));

        KnapsackSolution solution = solver.solve(items, 10);

        assertThat(solution.selectedIndexes()).containsExactly(1, 2);
        assertThat(solution.totalValue()).isEqualTo(40);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("solvers")
    void amongEqualValuesPrefersLighterSelection(String name, KnapsackSolver solver) {
        List<KnapsackItem> items = List.of(
                new KnapsackItem(0, 10, 100),
                new KnapsackItem(1, 4, 50),
                new KnapsackItem(2, 4, 50));

        KnapsackSolution solution = solver.solve(items, 10);

        assertThat(solution.totalValue()).isEqualTo(100);
        assertThat(solution.totalWeight()).isEqualTo(8);
        assertThat(solution.selectedIndexes()).containsExactly(1, 2);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("solvers")
    void amongIdenticalItemsPrefersEarlierInput(String name, KnapsackSolver solver) {
        List<KnapsackItem> items = List.of(
                new KnapsackItem(0, 5, 10),
                new KnapsackItem(1, 5, 10),
                new KnapsackItem(2, 5, 10));

        KnapsackSolution solution = solver.solve(items, 10);

        assertThat(solution.selectedIndexes()).containsExactly(0, 1);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("solvers")
    void neverSelectsZeroValueItems(String name, KnapsackSolver solver) {
        List<KnapsackItem> items = List.of(new KnapsackItem(0, 3, 0), new KnapsackItem(1, 4, 7));

        KnapsackSolution solution = solver.solve(items, 10);

        assertThat(solution.selectedIndexes()).containsExactly(1);
        assertThat(solution.totalWeight()).isEqualTo(4);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("solvers")
    void handlesLargeValuesWithoutOverflow(String name, KnapsackSolver solver) {
        long bigValue = 4_000_000_000_000_000L; // 4e18, two of them would overflow a long
        List<KnapsackItem> items = List.of(
                new KnapsackItem(0, 6, bigValue),
                new KnapsackItem(1, 6, bigValue),
                new KnapsackItem(2, 1, 1));

        KnapsackSolution solution = solver.solve(items, 7);

        assertThat(solution.selectedIndexes()).containsExactly(0, 2);
        assertThat(solution.totalValue()).isEqualTo(bigValue + 1);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("solvers")
    void matchesBruteForceOnRandomInstances(String name, KnapsackSolver solver) {
        Random random = new Random(20260910);
        for (int round = 0; round < 300; round++) {
            int n = 1 + random.nextInt(12);
            List<KnapsackItem> items = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                items.add(new KnapsackItem(i, 1 + random.nextInt(30), random.nextInt(50)));
            }
            long capacity = random.nextInt(80);

            KnapsackSolution expected = bruteForce(items, capacity);
            KnapsackSolution actual = solver.solve(items, capacity);

            assertThat(actual.totalValue()).as("value, round %d", round).isEqualTo(expected.totalValue());
            assertThat(actual.totalWeight()).as("weight, round %d", round).isEqualTo(expected.totalWeight());
            assertThat(actual.totalWeight()).isLessThanOrEqualTo(capacity);
            assertThat(sumWeight(items, actual.selectedIndexes())).isEqualTo(actual.totalWeight());
            assertThat(sumValue(items, actual.selectedIndexes())).isEqualTo(actual.totalValue());
        }
    }

    /** Exhaustive reference: max value, then min weight. */
    private static KnapsackSolution bruteForce(List<KnapsackItem> items, long capacity) {
        long bestValue = 0;
        long bestWeight = 0;
        int n = items.size();
        for (int mask = 0; mask < (1 << n); mask++) {
            long weight = 0;
            long value = 0;
            for (int i = 0; i < n; i++) {
                if ((mask & (1 << i)) != 0) {
                    weight += items.get(i).weight();
                    value += items.get(i).value();
                }
            }
            if (weight <= capacity && (value > bestValue || (value == bestValue && weight < bestWeight))) {
                bestValue = value;
                bestWeight = weight;
            }
        }
        return new KnapsackSolution(List.of(), bestWeight, bestValue);
    }

    private static long sumWeight(List<KnapsackItem> items, List<Integer> indexes) {
        return indexes.stream().mapToLong(i -> items.get(i).weight()).sum();
    }

    private static long sumValue(List<KnapsackItem> items, List<Integer> indexes) {
        return indexes.stream().mapToLong(i -> items.get(i).value()).sum();
    }
}
