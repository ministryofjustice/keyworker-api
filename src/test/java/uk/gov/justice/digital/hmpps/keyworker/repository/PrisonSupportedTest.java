package uk.gov.justice.digital.hmpps.keyworker.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.digital.hmpps.keyworker.model.PrisonSupported;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
@Transactional
class PrisonSupportedTest {
    @Autowired
    private PrisonSupportedRepository repository;

    @Test
    void givenATransientKeyworkerWhenPersistedItShoudBeRetrievableById() {

        final var transientEntity = transientEntity();

        final var entity = transientEntity.toBuilder().build();

        final var persistedEntity = repository.save(entity);

        TestTransaction.flagForCommit();
        TestTransaction.end();

        assertThat(persistedEntity.getPrisonId()).isNotNull();

        TestTransaction.start();

        final var retrievedEntity = repository.findById(entity.getPrisonId()).orElseThrow();

        // equals only compares the business key columns: prisonId
        assertThat(retrievedEntity).isEqualTo(transientEntity);

        assertThat(retrievedEntity.isMigrated()).isEqualTo(transientEntity.isMigrated());
        assertThat(retrievedEntity.isAutoAllocate()).isEqualTo(transientEntity.isAutoAllocate());
        assertThat(retrievedEntity.getMigratedDateTime()).isEqualTo(transientEntity.getMigratedDateTime());
        assertThat(retrievedEntity.getCapacityTier1()).isEqualTo(transientEntity.getCapacityTier1());
        assertThat(retrievedEntity.getCapacityTier2()).isEqualTo(transientEntity.getCapacityTier2());
    }

    private PrisonSupported transientEntity() {
        return PrisonSupported
                .builder()
                .prisonId("PRISON")
                .migrated(true)
                .autoAllocate(true)
                .migratedDateTime(LocalDateTime.now())
                .capacityTier1(3)
                .capacityTier2(4)
                .build();
    }
}
