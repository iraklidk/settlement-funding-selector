package ge.kursi.settlement.algorithm;

import ge.kursi.settlement.config.FundingProperties;
import ge.kursi.settlement.domain.CandidateInstruction;
import ge.kursi.settlement.domain.FundingSelection;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Chooses the set of instructions that maximises total expected fee without exceeding the
 * available settlement balance (a 0/1 knapsack problem).
 * <p>
 * Money is converted to minor units (scale {@value #MONEY_SCALE}) so all arithmetic is exact
 * integer arithmetic. Before solving, the capacity space is compressed by:
 * <ol>
 *   <li>dropping instructions that alone exceed the balance;</li>
 *   <li>dividing every amount and the balance by their greatest common divisor
 *       (round amounts such as 7,000.00 collapse the capacity from 2,000,000 minor units to 20).</li>
 * </ol>
 * If the resulting DP table stays within {@link FundingProperties#dpMaxCells()} the
 * {@link DynamicProgrammingKnapsackSolver} is used; otherwise the capacity-independent
 * {@link BranchAndBoundKnapsackSolver} is used. Both are exact.
 */
@Component
public class FundingSelector {

    public static final int MONEY_SCALE = 2;

    private static final Logger log = LoggerFactory.getLogger(FundingSelector.class);

    private final KnapsackSolver dpSolver;
    private final KnapsackSolver branchAndBoundSolver;
    private final long dpMaxCells;

    @Autowired
    public FundingSelector(FundingProperties properties) {
        this(new DynamicProgrammingKnapsackSolver(), new BranchAndBoundKnapsackSolver(), properties.dpMaxCells());
    }

    FundingSelector(KnapsackSolver dpSolver, KnapsackSolver branchAndBoundSolver, long dpMaxCells) {
        this.dpSolver = dpSolver;
        this.branchAndBoundSolver = branchAndBoundSolver;
        this.dpMaxCells = dpMaxCells;
    }

    public FundingSelection select(List<CandidateInstruction> candidates, BigDecimal availableBalance) {
        long capacity = toMinorUnits(availableBalance);
        if (capacity < 0) {
            throw new IllegalArgumentException("availableBalance must not be negative");
        }

        List<KnapsackItem> items = new ArrayList<>(candidates.size());
        long gcd = capacity;
        for (int i = 0; i < candidates.size(); i++) {
            CandidateInstruction candidate = candidates.get(i);
            long amount = toMinorUnits(candidate.instructionAmount());
            long fee = toMinorUnits(candidate.expectedFee());
            if (amount > capacity) {
                continue; // cannot be funded on its own, let alone together with others
            }
            items.add(new KnapsackItem(i, amount, fee));
            gcd = gcd(gcd, amount);
        }
        if (items.isEmpty()) {
            return FundingSelection.of(List.of());
        }

        List<KnapsackItem> scaledItems = new ArrayList<>(items.size());
        for (KnapsackItem item : items) {
            scaledItems.add(new KnapsackItem(item.index(), item.weight() / gcd, item.value()));
        }
        long scaledCapacity = capacity / gcd;

        KnapsackSolver solver = chooseSolver(scaledItems.size(), scaledCapacity);
        KnapsackSolution solution = solver.solve(scaledItems, scaledCapacity);

        List<CandidateInstruction> selected = new ArrayList<>(solution.selectedIndexes().size());
        for (int index : solution.selectedIndexes()) {
            selected.add(candidates.get(index));
        }
        return FundingSelection.of(selected);
    }

    private KnapsackSolver chooseSolver(int itemCount, long capacity) {
        BigInteger cells = BigInteger.valueOf(itemCount).multiply(BigInteger.valueOf(capacity + 1));
        boolean useDp = cells.compareTo(BigInteger.valueOf(dpMaxCells)) <= 0;
        log.debug("Knapsack instance: items={}, capacity={}, cells={}, solver={}",
                itemCount, capacity, cells, useDp ? "dp" : "branch-and-bound");
        return useDp ? dpSolver : branchAndBoundSolver;
    }

    static long toMinorUnits(BigDecimal amount) {
        return amount.setScale(MONEY_SCALE, RoundingMode.UNNECESSARY).unscaledValue().longValueExact();
    }

    private static long gcd(long a, long b) {
        while (b != 0) {
            long t = a % b;
            a = b;
            b = t;
        }
        return a;
    }
}
