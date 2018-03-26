package uk.gov.justice.digital.hmpps.keyworker.repository;

import lombok.val;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.digital.hmpps.keyworker.model.Keyworker;
import uk.gov.justice.digital.hmpps.keyworker.model.KeyworkerStatus;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Transactional
public class KeyworkerRepositoryTest {

    private static long currentId;

    @Autowired
    private KeyworkerRepository repository;

    @Test
    public void givenATransientKeyworkerWhenPersistedItShoudBeRetrievableById() {

        val transientEntity = transientEntity();

        val entity = transientEntity.toBuilder().build();

        val persistedEntity = repository.save(entity);

        TestTransaction.flagForCommit();
        TestTransaction.end();

        assertThat(persistedEntity.getStaffId()).isNotNull();

        TestTransaction.start();

        val retrievedEntity = repository.findOne(entity.getStaffId());

        // equals only compares the business key columns: staffId
        assertThat(retrievedEntity).isEqualTo(transientEntity);

        assertThat(retrievedEntity.getStatus()).isEqualTo(transientEntity.getStatus());
        assertThat(retrievedEntity.getCapacity()).isEqualTo(transientEntity.getCapacity());
    }

    @Test
    public void givenAPersistentInstanceThenNullableValuesAreUpdateable() {

        val entity = repository.save(transientEntity());
        TestTransaction.flagForCommit();
        TestTransaction.end();

        TestTransaction.start();
        val retrievedEntity = repository.findOne(entity.getStaffId());

        TestTransaction.flagForCommit();
        TestTransaction.end();

        TestTransaction.start();

        val persistedUpdates = repository.findOne(entity.getStaffId());
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
