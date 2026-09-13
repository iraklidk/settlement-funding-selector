package ge.kursi.settlement.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Runs the real Flyway migration against H2 (PostgreSQL mode) and verifies the JPA mapping,
 * the audit ordering and the database-level constraints.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class FundingRequestRepositoryTest {

    @Autowired
    private FundingRequestRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    private static FundingRequestEntity run(Instant createdAt, String... references) {
        FundingRequestEntity entity = new FundingRequestEntity(
                UUID.randomUUID(), new BigDecimal("100.00"), new BigDecimal("10.00"), new BigDecimal("1.00"), createdAt);
        for (int i = 0; i < references.length; i++) {
            entity.addInstruction(new FundingInstructionEntity(
                    i, references[i], new BigDecimal("10.00"), new BigDecimal("1.00"), i == 0));
        }
        return entity;
    }

    @Test
    void savesAndReloadsRunWithInstructionsInInputOrder() {
        FundingRequestEntity saved = repository.saveAndFlush(run(Instant.parse("2026-09-07T09:00:00Z"), "C", "A", "B"));
        entityManager.clear();

        Optional<FundingRequestEntity> reloaded = repository.findWithInstructionsById(saved.getId());

        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().getInstructions())
                .extracting(FundingInstructionEntity::getInstructionReference)
                .containsExactly("C", "A", "B");
        assertThat(reloaded.get().getInstructions())
                .extracting(FundingInstructionEntity::isSelected)
                .containsExactly(true, false, false);
        assertThat(reloaded.get().getCandidateCount()).isEqualTo(3);
        assertThat(reloaded.get().getSelectedCount()).isEqualTo(1);
        assertThat(reloaded.get().getCreatedAt()).isEqualTo(Instant.parse("2026-09-07T09:00:00Z"));
    }

    @Test
    void listsRunsNewestFirst() {
        FundingRequestEntity oldest = repository.save(run(Instant.parse("2026-09-01T09:00:00Z"), "X"));
        FundingRequestEntity newest = repository.save(run(Instant.parse("2026-09-03T09:00:00Z"), "X"));
        FundingRequestEntity middle = repository.save(run(Instant.parse("2026-09-02T09:00:00Z"), "X"));
        repository.flush();

        Page<FundingRequestEntity> firstPage = repository.findAllByOrderByCreatedAtDescIdDesc(PageRequest.of(0, 2));
        Page<FundingRequestEntity> secondPage = repository.findAllByOrderByCreatedAtDescIdDesc(PageRequest.of(1, 2));

        assertThat(firstPage.getTotalElements()).isEqualTo(3);
        assertThat(firstPage.getContent()).extracting(FundingRequestEntity::getId)
                .containsExactly(newest.getId(), middle.getId());
        assertThat(secondPage.getContent()).extracting(FundingRequestEntity::getId)
                .containsExactly(oldest.getId());
    }

    @Test
    void rejectsDuplicateReferenceWithinOneRun() {
        FundingRequestEntity entity = run(Instant.now(), "DUP", "DUP");

        assertThatThrownBy(() -> repository.saveAndFlush(entity))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deletingRunCascadesToInstructions() {
        FundingRequestEntity saved = repository.saveAndFlush(run(Instant.now(), "A", "B"));
        entityManager.clear();

        repository.deleteById(saved.getId());
        repository.flush();

        List<?> orphans = entityManager.getEntityManager()
                .createQuery("select i from FundingInstructionEntity i where i.request.id = :id")
                .setParameter("id", saved.getId())
                .getResultList();
        assertThat(orphans).isEmpty();
    }
}
