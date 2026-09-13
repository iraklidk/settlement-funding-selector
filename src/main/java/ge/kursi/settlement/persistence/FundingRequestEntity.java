package ge.kursi.settlement.persistence;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "funding_request")
public class FundingRequestEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "available_settlement_balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal availableSettlementBalance;

    @Column(name = "total_settlement_consumed", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalSettlementConsumed;

    @Column(name = "total_expected_fee", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalExpectedFee;

    @Column(name = "candidate_count", nullable = false)
    private int candidateCount;

    @Column(name = "selected_count", nullable = false)
    private int selectedCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "request", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("inputOrder ASC")
    private List<FundingInstructionEntity> instructions = new ArrayList<>();

    protected FundingRequestEntity() {
        // for JPA
    }

    public FundingRequestEntity(UUID id,
                                BigDecimal availableSettlementBalance,
                                BigDecimal totalSettlementConsumed,
                                BigDecimal totalExpectedFee,
                                Instant createdAt) {
        this.id = id;
        this.availableSettlementBalance = availableSettlementBalance;
        this.totalSettlementConsumed = totalSettlementConsumed;
        this.totalExpectedFee = totalExpectedFee;
        this.createdAt = createdAt;
    }

    public void addInstruction(FundingInstructionEntity instruction) {
        instruction.setRequest(this);
        instructions.add(instruction);
        candidateCount = instructions.size();
        if (instruction.isSelected()) {
            selectedCount++;
        }
    }

    public UUID getId() {
        return id;
    }

    public BigDecimal getAvailableSettlementBalance() {
        return availableSettlementBalance;
    }

    public BigDecimal getTotalSettlementConsumed() {
        return totalSettlementConsumed;
    }

    public BigDecimal getTotalExpectedFee() {
        return totalExpectedFee;
    }

    public int getCandidateCount() {
        return candidateCount;
    }

    public int getSelectedCount() {
        return selectedCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<FundingInstructionEntity> getInstructions() {
        return instructions;
    }
}
