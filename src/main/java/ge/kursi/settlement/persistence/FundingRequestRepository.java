package ge.kursi.settlement.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FundingRequestRepository extends JpaRepository<FundingRequestEntity, UUID> {

    /** Loads a run together with all its candidate instructions in a single query. */
    @EntityGraph(attributePaths = "instructions")
    Optional<FundingRequestEntity> findWithInstructionsById(UUID id);

    /** Audit trail, newest first. Instructions stay lazy: the listing only needs the header row. */
    Page<FundingRequestEntity> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);
}
