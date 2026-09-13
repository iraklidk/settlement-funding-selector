package ge.kursi.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ge.kursi.settlement.algorithm.FundingSelector;
import ge.kursi.settlement.domain.CandidateInstruction;
import ge.kursi.settlement.domain.FundingResult;
import ge.kursi.settlement.domain.FundingRunSummary;
import ge.kursi.settlement.domain.FundingSelection;
import ge.kursi.settlement.persistence.FundingInstructionEntity;
import ge.kursi.settlement.persistence.FundingRequestEntity;
import ge.kursi.settlement.persistence.FundingRequestRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class SettlementFundingServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-07T09:00:00.123456789Z");

    @Mock
    private FundingSelector selector;

    @Mock
    private FundingRequestRepository repository;

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private SettlementFundingService service;

    @BeforeEach
    void setUp() {
        service = new SettlementFundingService(selector, repository, clock);
    }

    @Captor
    private ArgumentCaptor<FundingRequestEntity> entityCaptor;

    private static CandidateInstruction instruction(String ref, String amount, String fee) {
        return new CandidateInstruction(ref, new BigDecimal(amount), new BigDecimal(fee));
    }

    @Test
    void fundPersistsEveryCandidateWithSelectionFlagAndReturnsResult() {
        CandidateInstruction a = instruction("INS-A", "7000.00", "150.00");
        CandidateInstruction b = instruction("INS-B", "9000.00", "210.00");
        CandidateInstruction c = instruction("INS-C", "15000.00", "999.00");
        when(selector.select(anyList(), any())).thenReturn(FundingSelection.of(List.of(a, b)));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        FundingResult result = service.fund(new BigDecimal("20000"), List.of(a, b, c));

        verify(repository).save(entityCaptor.capture());
        FundingRequestEntity saved = entityCaptor.getValue();
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getAvailableSettlementBalance()).isEqualTo(new BigDecimal("20000.00"));
        assertThat(saved.getTotalSettlementConsumed()).isEqualTo(new BigDecimal("16000.00"));
        assertThat(saved.getTotalExpectedFee()).isEqualTo(new BigDecimal("360.00"));
        assertThat(saved.getCandidateCount()).isEqualTo(3);
        assertThat(saved.getSelectedCount()).isEqualTo(2);
        assertThat(saved.getCreatedAt()).isEqualTo(Instant.parse("2026-09-07T09:00:00.123456Z"));
        assertThat(saved.getInstructions())
                .extracting(FundingInstructionEntity::getInstructionReference, FundingInstructionEntity::isSelected,
                        FundingInstructionEntity::getInputOrder)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("INS-A", true, 0),
                        org.assertj.core.groups.Tuple.tuple("INS-B", true, 1),
                        org.assertj.core.groups.Tuple.tuple("INS-C", false, 2));
        assertThat(saved.getInstructions()).allSatisfy(i -> assertThat(i.getRequest()).isSameAs(saved));

        assertThat(result.requestId()).isEqualTo(saved.getId());
        assertThat(result.selectedInstructions()).containsExactly(a, b);
        assertThat(result.candidateInstructions()).containsExactly(a, b, c);
        assertThat(result.totalSettlementConsumed()).isEqualTo(new BigDecimal("16000.00"));
        assertThat(result.totalExpectedFee()).isEqualTo(new BigDecimal("360.00"));
        assertThat(result.createdAt()).isEqualTo(saved.getCreatedAt());
    }

    @Test
    void fundNormalisesReferencesAndMoneyScaleBeforeSelecting() {
        when(selector.select(anyList(), any())).thenReturn(FundingSelection.of(List.of()));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.fund(new BigDecimal("100"), List.of(instruction("  INS-1 ", "10", "1.5")));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CandidateInstruction>> candidates = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<BigDecimal> balance = ArgumentCaptor.forClass(BigDecimal.class);
        verify(selector).select(candidates.capture(), balance.capture());
        assertThat(balance.getValue()).isEqualTo(new BigDecimal("100.00"));
        assertThat(candidates.getValue()).containsExactly(instruction("INS-1", "10.00", "1.50"));
    }

    @Test
    void fundRejectsDuplicateReferencesWithoutTouchingSelectorOrDatabase() {
        List<CandidateInstruction> candidates = List.of(
                instruction("INS-1", "10", "1"),
                instruction("INS-1 ", "20", "2"));

        assertThatThrownBy(() -> service.fund(new BigDecimal("100"), candidates))
                .isInstanceOf(InvalidFundingRequestException.class)
                .hasMessageContaining("Duplicate instructionReference 'INS-1'");
        verifyNoInteractions(selector, repository);
    }

    @Test
    void getByIdMapsPersistedEntity() {
        UUID id = UUID.randomUUID();
        FundingRequestEntity entity = new FundingRequestEntity(
                id, new BigDecimal("50.00"), new BigDecimal("30.00"), new BigDecimal("3.00"), NOW);
        entity.addInstruction(new FundingInstructionEntity(0, "X", new BigDecimal("30.00"), new BigDecimal("3.00"), true));
        entity.addInstruction(new FundingInstructionEntity(1, "Y", new BigDecimal("40.00"), new BigDecimal("2.00"), false));
        when(repository.findWithInstructionsById(id)).thenReturn(Optional.of(entity));

        FundingResult result = service.getById(id);

        assertThat(result.requestId()).isEqualTo(id);
        assertThat(result.availableSettlementBalance()).isEqualTo(new BigDecimal("50.00"));
        assertThat(result.candidateInstructions()).extracting(CandidateInstruction::instructionReference)
                .containsExactly("X", "Y");
        assertThat(result.selectedInstructions()).extracting(CandidateInstruction::instructionReference)
                .containsExactly("X");
        assertThat(result.createdAt()).isEqualTo(NOW);
    }

    @Test
    void getByIdThrowsWhenMissing() {
        UUID id = UUID.randomUUID();
        when(repository.findWithInstructionsById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(id))
                .isInstanceOf(FundingRequestNotFoundException.class)
                .hasMessageContaining(id.toString());
    }

    @Test
    void listMapsPageToSummaries() {
        FundingRequestEntity entity = new FundingRequestEntity(
                UUID.randomUUID(), new BigDecimal("50.00"), new BigDecimal("30.00"), new BigDecimal("3.00"), NOW);
        entity.addInstruction(new FundingInstructionEntity(0, "X", new BigDecimal("30.00"), new BigDecimal("3.00"), true));
        PageRequest pageable = PageRequest.of(0, 20);
        when(repository.findAllByOrderByCreatedAtDescIdDesc(pageable))
                .thenReturn(new PageImpl<>(List.of(entity), pageable, 1));

        Page<FundingRunSummary> page = service.list(pageable);

        assertThat(page.getTotalElements()).isEqualTo(1);
        FundingRunSummary summary = page.getContent().get(0);
        assertThat(summary.requestId()).isEqualTo(entity.getId());
        assertThat(summary.candidateCount()).isEqualTo(1);
        assertThat(summary.selectedCount()).isEqualTo(1);
        assertThat(summary.totalExpectedFee()).isEqualTo(new BigDecimal("3.00"));
    }
}
