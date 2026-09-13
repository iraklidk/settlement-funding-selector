package ge.kursi.settlement.service;

import ge.kursi.settlement.algorithm.FundingSelector;
import ge.kursi.settlement.domain.CandidateInstruction;
import ge.kursi.settlement.domain.FundingResult;
import ge.kursi.settlement.domain.FundingRunSummary;
import ge.kursi.settlement.domain.FundingSelection;
import ge.kursi.settlement.persistence.FundingInstructionEntity;
import ge.kursi.settlement.persistence.FundingRequestEntity;
import ge.kursi.settlement.persistence.FundingRequestRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates a funding run: validates the request semantically, runs the selection
 * algorithm and persists both the input and the outcome as one audit record.
 */
@Service
public class SettlementFundingService {

    private static final Logger log = LoggerFactory.getLogger(SettlementFundingService.class);

    private final FundingSelector fundingSelector;
    private final FundingRequestRepository repository;
    private final Clock clock;

    public SettlementFundingService(FundingSelector fundingSelector,
                                    FundingRequestRepository repository,
                                    Clock clock) {
        this.fundingSelector = fundingSelector;
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public FundingResult fund(BigDecimal availableSettlementBalance, List<CandidateInstruction> candidates) {
        List<CandidateInstruction> normalised = normalise(candidates);
        BigDecimal balance = money(availableSettlementBalance);

        FundingSelection selection = fundingSelector.select(normalised, balance);

        FundingRequestEntity entity = new FundingRequestEntity(
                UUID.randomUUID(),
                balance,
                money(selection.totalSettlementConsumed()),
                money(selection.totalExpectedFee()),
                // DB precision is microseconds; truncate so the 201 body equals later GET bodies.
                Instant.now(clock).truncatedTo(ChronoUnit.MICROS));

        Set<String> selectedReferences = new HashSet<>();
        for (CandidateInstruction selected : selection.selectedInstructions()) {
            selectedReferences.add(selected.instructionReference());
        }
        for (int i = 0; i < normalised.size(); i++) {
            CandidateInstruction candidate = normalised.get(i);
            entity.addInstruction(new FundingInstructionEntity(
                    i,
                    candidate.instructionReference(),
                    candidate.instructionAmount(),
                    candidate.expectedFee(),
                    selectedReferences.contains(candidate.instructionReference())));
        }

        repository.save(entity);
        log.info("Funding run {}: {}/{} instructions selected, consumed {} of {}, fee {}",
                entity.getId(), entity.getSelectedCount(), entity.getCandidateCount(),
                entity.getTotalSettlementConsumed(), balance, entity.getTotalExpectedFee());
        return toResult(entity);
    }

    @Transactional(readOnly = true)
    public FundingResult getById(UUID requestId) {
        return repository.findWithInstructionsById(requestId)
                .map(SettlementFundingService::toResult)
                .orElseThrow(() -> new FundingRequestNotFoundException(requestId));
    }

    @Transactional(readOnly = true)
    public Page<FundingRunSummary> list(Pageable pageable) {
        return repository.findAllByOrderByCreatedAtDescIdDesc(pageable).map(SettlementFundingService::toSummary);
    }

    /** Trims references, normalises money scale and rejects duplicate references. */
    private static List<CandidateInstruction> normalise(List<CandidateInstruction> candidates) {
        List<CandidateInstruction> result = new ArrayList<>(candidates.size());
        Set<String> seen = new HashSet<>();
        for (CandidateInstruction candidate : candidates) {
            String reference = candidate.instructionReference().trim();
            if (!seen.add(reference)) {
                throw new InvalidFundingRequestException(
                        "Duplicate instructionReference '" + reference + "': references must be unique within a request");
            }
            result.add(new CandidateInstruction(reference,
                    money(candidate.instructionAmount()),
                    money(candidate.expectedFee())));
        }
        return result;
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(FundingSelector.MONEY_SCALE, RoundingMode.UNNECESSARY);
    }

    private static FundingResult toResult(FundingRequestEntity entity) {
        List<CandidateInstruction> candidates = new ArrayList<>(entity.getInstructions().size());
        List<CandidateInstruction> selected = new ArrayList<>(entity.getSelectedCount());
        for (FundingInstructionEntity instruction : entity.getInstructions()) {
            CandidateInstruction candidate = new CandidateInstruction(
                    instruction.getInstructionReference(),
                    instruction.getInstructionAmount(),
                    instruction.getExpectedFee());
            candidates.add(candidate);
            if (instruction.isSelected()) {
                selected.add(candidate);
            }
        }
        return new FundingResult(
                entity.getId(),
                entity.getAvailableSettlementBalance(),
                candidates,
                selected,
                entity.getTotalSettlementConsumed(),
                entity.getTotalExpectedFee(),
                entity.getCreatedAt());
    }

    private static FundingRunSummary toSummary(FundingRequestEntity entity) {
        return new FundingRunSummary(
                entity.getId(),
                entity.getAvailableSettlementBalance(),
                entity.getTotalSettlementConsumed(),
                entity.getTotalExpectedFee(),
                entity.getCandidateCount(),
                entity.getSelectedCount(),
                entity.getCreatedAt());
    }
}
