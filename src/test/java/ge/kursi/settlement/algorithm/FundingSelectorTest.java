package ge.kursi.settlement.algorithm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ge.kursi.settlement.config.FundingProperties;
import ge.kursi.settlement.domain.CandidateInstruction;
import ge.kursi.settlement.domain.FundingSelection;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class FundingSelectorTest {

    private static CandidateInstruction instruction(String ref, String amount, String fee) {
        return new CandidateInstruction(ref, new BigDecimal(amount), new BigDecimal(fee));
    }

    @Nested
    class EndToEnd {

        private final FundingSelector selector = new FundingSelector(new FundingProperties(50_000_000));

        @Test
        void selectsAssignmentExample() {
            List<CandidateInstruction> candidates = List.of(
                    instruction("INS-2001", "7000", "150"),
                    instruction("INS-2002", "9000", "210"),
                    instruction("INS-2003", "4000", "90"),
                    instruction("INS-2004", "6000", "130"));

            FundingSelection selection = selector.select(candidates, new BigDecimal("20000"));

            assertThat(selection.selectedInstructions())
                    .extracting(CandidateInstruction::instructionReference)
                    .containsExactly("INS-2001", "INS-2002", "INS-2003");
            assertThat(selection.totalSettlementConsumed()).isEqualByComparingTo("20000");
            assertThat(selection.totalExpectedFee()).isEqualByComparingTo("450");
        }

        @Test
        void returnsEmptySelectionWhenNothingFits() {
            List<CandidateInstruction> candidates = List.of(
                    instruction("A", "500.00", "10"),
                    instruction("B", "600.00", "12"));

            FundingSelection selection = selector.select(candidates, new BigDecimal("499.99"));

            assertThat(selection.selectedInstructions()).isEmpty();
            assertThat(selection.totalSettlementConsumed()).isEqualByComparingTo("0");
            assertThat(selection.totalExpectedFee()).isEqualByComparingTo("0");
        }

        @Test
        void returnsEmptySelectionForZeroBalance() {
            FundingSelection selection = selector.select(List.of(instruction("A", "0.01", "1")), BigDecimal.ZERO);

            assertThat(selection.selectedInstructions()).isEmpty();
        }

        @Test
        void handlesFractionalAmountsExactly() {
            // 0.1 + 0.2 must equal 0.3 exactly: minor-unit arithmetic, not floating point.
            List<CandidateInstruction> candidates = List.of(
                    instruction("A", "0.10", "1.00"),
                    instruction("B", "0.20", "1.00"),
                    instruction("C", "0.30", "1.50"));

            FundingSelection selection = selector.select(candidates, new BigDecimal("0.30"));

            assertThat(selection.selectedInstructions())
                    .extracting(CandidateInstruction::instructionReference)
                    .containsExactly("A", "B");
            assertThat(selection.totalSettlementConsumed()).isEqualByComparingTo("0.30");
            assertThat(selection.totalExpectedFee()).isEqualByComparingTo("2.00");
        }

        @Test
        void preservesInputOrderInSelection() {
            List<CandidateInstruction> candidates = List.of(
                    instruction("Z", "1", "1"),
                    instruction("Y", "1", "1"),
                    instruction("X", "1", "1"));

            FundingSelection selection = selector.select(candidates, new BigDecimal("3"));

            assertThat(selection.selectedInstructions())
                    .extracting(CandidateInstruction::instructionReference)
                    .containsExactly("Z", "Y", "X");
        }

        @Test
        void branchAndBoundFallbackMatchesDpOnUnalignedAmounts() {
            // Cent-level amounts defeat the gcd compression, so the capacity stays large.
            // Force each solver in turn and check they agree, proving the fallback is exact.
            List<CandidateInstruction> candidates = new ArrayList<>();
            for (int i = 0; i < 40; i++) {
                BigDecimal amount = new BigDecimal("1000.00").add(new BigDecimal("123.57").multiply(BigDecimal.valueOf(i)));
                BigDecimal fee = new BigDecimal("10.00").add(new BigDecimal("1.31").multiply(BigDecimal.valueOf(i % 7)));
                candidates.add(new CandidateInstruction("INS-" + i, amount, fee));
            }
            BigDecimal balance = new BigDecimal("12345.67");

            FundingSelection viaDp = new FundingSelector(new FundingProperties(Long.MAX_VALUE)).select(candidates, balance);
            FundingSelection viaBb = new FundingSelector(new FundingProperties(1)).select(candidates, balance);

            assertThat(viaDp.totalSettlementConsumed()).isLessThanOrEqualTo(balance);
            assertThat(viaDp.selectedInstructions()).isNotEmpty();
            assertThat(viaBb.totalExpectedFee()).isEqualByComparingTo(viaDp.totalExpectedFee());
            assertThat(viaBb.totalSettlementConsumed()).isEqualByComparingTo(viaDp.totalSettlementConsumed());
        }

        @Test
        void rejectsMoreThanTwoDecimalPlaces() {
            assertThatThrownBy(() -> selector.select(List.of(instruction("A", "1.005", "1")), BigDecimal.TEN))
                    .isInstanceOf(ArithmeticException.class);
        }
    }

    @Nested
    class SolverSelection {

        private final KnapsackSolver dp = mock(KnapsackSolver.class);
        private final KnapsackSolver bb = mock(KnapsackSolver.class);

        @Test
        void scalesByGcdAndUsesDpWhenTableIsSmall() {
            FundingSelector selector = new FundingSelector(dp, bb, 1_000);
            when(dp.solve(anyList(), anyLong())).thenReturn(new KnapsackSolution(List.of(1), 9, 210));
            List<CandidateInstruction> candidates = List.of(
                    instruction("INS-2001", "7000", "150"),
                    instruction("INS-2002", "9000", "210"));

            FundingSelection selection = selector.select(candidates, new BigDecimal("20000"));

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<KnapsackItem>> items = ArgumentCaptor.forClass(List.class);
            ArgumentCaptor<Long> capacity = ArgumentCaptor.forClass(Long.class);
            verify(dp).solve(items.capture(), capacity.capture());
            verifyNoInteractions(bb);
            // 700000 / 900000 / 2000000 minor units share a gcd of 100000
            assertThat(capacity.getValue()).isEqualTo(20);
            assertThat(items.getValue()).containsExactly(
                    new KnapsackItem(0, 7, 15000),
                    new KnapsackItem(1, 9, 21000));
            assertThat(selection.selectedInstructions()).containsExactly(candidates.get(1));
        }

        @Test
        void fallsBackToBranchAndBoundWhenTableIsTooLarge() {
            FundingSelector selector = new FundingSelector(dp, bb, 10);
            when(bb.solve(anyList(), anyLong())).thenReturn(KnapsackSolution.EMPTY);

            selector.select(List.of(instruction("A", "1.01", "1"), instruction("B", "2.03", "1")), new BigDecimal("50"));

            verify(bb).solve(anyList(), anyLong());
            verifyNoInteractions(dp);
        }

        @Test
        void dropsInstructionsLargerThanBalanceBeforeSolving() {
            FundingSelector selector = new FundingSelector(dp, bb, 1_000_000);
            when(dp.solve(anyList(), anyLong())).thenReturn(KnapsackSolution.EMPTY);

            selector.select(List.of(
                    instruction("TOO-BIG", "100.01", "999"),
                    instruction("FITS", "100.00", "1")), new BigDecimal("100.00"));

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<KnapsackItem>> items = ArgumentCaptor.forClass(List.class);
            verify(dp).solve(items.capture(), anyLong());
            assertThat(items.getValue()).extracting(KnapsackItem::index).containsExactly(1);
        }

        @Test
        void skipsSolversEntirelyWhenNothingCanFit() {
            FundingSelector selector = new FundingSelector(dp, bb, 1_000_000);

            FundingSelection selection = selector.select(List.of(instruction("A", "5", "1")), new BigDecimal("4.99"));

            verifyNoInteractions(dp, bb);
            assertThat(selection.selectedInstructions()).isEmpty();
        }
    }
}
