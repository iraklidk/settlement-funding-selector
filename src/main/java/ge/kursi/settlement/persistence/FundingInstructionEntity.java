package ge.kursi.settlement.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "funding_instruction")
public class FundingInstructionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false, updatable = false)
    private FundingRequestEntity request;

    @Column(name = "input_order", nullable = false)
    private int inputOrder;

    @Column(name = "instruction_reference", nullable = false, length = 128)
    private String instructionReference;

    @Column(name = "instruction_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal instructionAmount;

    @Column(name = "expected_fee", nullable = false, precision = 19, scale = 2)
    private BigDecimal expectedFee;

    @Column(name = "selected", nullable = false)
    private boolean selected;

    protected FundingInstructionEntity() {
        // for JPA
    }

    public FundingInstructionEntity(int inputOrder,
                                    String instructionReference,
                                    BigDecimal instructionAmount,
                                    BigDecimal expectedFee,
                                    boolean selected) {
        this.inputOrder = inputOrder;
        this.instructionReference = instructionReference;
        this.instructionAmount = instructionAmount;
        this.expectedFee = expectedFee;
        this.selected = selected;
    }

    void setRequest(FundingRequestEntity request) {
        this.request = request;
    }

    public Long getId() {
        return id;
    }

    public FundingRequestEntity getRequest() {
        return request;
    }

    public int getInputOrder() {
        return inputOrder;
    }

    public String getInstructionReference() {
        return instructionReference;
    }

    public BigDecimal getInstructionAmount() {
        return instructionAmount;
    }

    public BigDecimal getExpectedFee() {
        return expectedFee;
    }

    public boolean isSelected() {
        return selected;
    }
}
