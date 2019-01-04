package uk.gov.justice.digital.hmpps.keyworker.repository;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.digital.hmpps.keyworker.model.PrisonSupported;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Transactional
public class PrisonSupportedTest {

    private static long currentId;

    @Autowired
    private PrisonSupportedRepository repository;

    @Test
    public void givenATransientKeyworkerWhenPersistedItShoudBeRetrievableById() {

        var transientEntity = transientEntity();

        var entity = transientEntity.toBuilder().build();

        var persistedEntity = repository.save(entity);

        TestTransaction.flagForCommit();
        TestTransaction.end();

        assertThat(persistedEntity.getPrisonId()).isNotNull();

        TestTransaction.start();

        var retrievedEntity = repository.findById(entity.getPrisonId()).orElseThrow();

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
