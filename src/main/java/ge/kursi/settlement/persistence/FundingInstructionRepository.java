package ge.kursi.settlement.persistence;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

//public interface FundingRequestRepository extends JpaRepository<FundingInstructionEntity, Long> {
//    List<FundingInstructionEntity> findBySelectedFalseAndInstructionAmountLessThanEqual(BigDecimal x);
//}

public interface FundingInstructionRepository extends JpaRepository<FundingInstructionEntity, Long> {
    List<FundingInstructionEntity> findBySelectedFalseAndInstructionAmountLessThanEqual(BigDecimal x);
}

