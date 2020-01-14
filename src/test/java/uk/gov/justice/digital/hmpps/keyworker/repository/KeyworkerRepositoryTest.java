package uk.gov.justice.digital.hmpps.keyworker.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.digital.hmpps.keyworker.model.Keyworker;
import uk.gov.justice.digital.hmpps.keyworker.model.KeyworkerStatus;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
@Transactional
class KeyworkerRepositoryTest {

    private static long currentId;

    @Autowired
    private KeyworkerRepository repository;

    @Test
    void givenATransientKeyworkerWhenPersistedItShoudBeRetrievableById() {

        final var transientEntity = transientEntity();

        final var entity = transientEntity.toBuilder().build();

        final var persistedEntity = repository.save(entity);

        TestTransaction.flagForCommit();
        TestTransaction.end();

        assertThat(persistedEntity.getStaffId()).isNotNull();

        TestTransaction.start();

        final var retrievedEntity = repository.findById(entity.getStaffId()).orElseThrow();

        // equals only compares the business key columns: staffId
        assertThat(retrievedEntity).isEqualTo(transientEntity);

        assertThat(retrievedEntity.getStatus()).isEqualTo(transientEntity.getStatus());
        assertThat(retrievedEntity.getCapacity()).isEqualTo(transientEntity.getCapacity());
    }

    @Test
    void givenAPersistentInstanceThenNullableValuesAreUpdateable() {

        final var entity = repository.save(transientEntity());
        TestTransaction.flagForCommit();
        TestTransaction.end();

        TestTransaction.start();
        repository.findById(entity.getStaffId());

        TestTransaction.flagForCommit();
        TestTransaction.end();

        TestTransaction.start();

        repository.findById(entity.getStaffId());
    }

    private Keyworker transientEntity() {
        return Keyworker
                .builder()
                .staffId(nextId())
                .status(KeyworkerStatus.ACTIVE)
                .capacity(6)
                .autoAllocationFlag(true)
                .build();
    }

    private static long nextId() {
        return currentId++;
    }
}
