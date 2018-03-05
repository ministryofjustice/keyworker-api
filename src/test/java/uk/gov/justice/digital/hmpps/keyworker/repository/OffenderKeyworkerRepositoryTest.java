package uk.gov.justice.digital.hmpps.keyworker.repository;

import lombok.val;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.digital.hmpps.keyworker.model.AllocationType;
import uk.gov.justice.digital.hmpps.keyworker.model.CreateUpdate;
import uk.gov.justice.digital.hmpps.keyworker.model.OffenderKeyworker;

import java.time.LocalDateTime;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)

@Transactional

public class OffenderKeyworkerRepositoryTest {

    private static final LocalDateTime ASSIGNED_DATE_TIME = LocalDateTime.of(2016,1, 2, 3, 4, 5);
    private static final LocalDateTime EXPIRY_DATE_TIME = LocalDateTime.of(2020, 12, 30, 9, 34, 56);
    private static long currentId;

    @Autowired
    private OffenderKeyworkerRepository repository;

    @Test
    public void givenATransientOffenderKeyworkerWhenPersistedIitShoudBeRetrievableById() {

        val transientEntity = transientEntity();

        val entity = transientEntity.toBuilder().build();

        val persistedEntity = repository.save(entity);

        TestTransaction.flagForCommit();
        TestTransaction.end();

        assertThat(persistedEntity.getOffenderKeyworkerId(), notNullValue());

        TestTransaction.start();

        val retrievedEntity = repository.findOne(entity.getOffenderKeyworkerId());

        // equals only compares the business key columns: offenderBookingId, staffId and assignedDateTime
        assertThat(retrievedEntity, is(equalTo(transientEntity)));

        assertThat(retrievedEntity.isActive(), is(true));
        assertThat(retrievedEntity.getAllocationType(), is(AllocationType.A));
        assertThat(retrievedEntity.getUserId(), is("The Assigning User"));
        assertThat(retrievedEntity.getAgencyLocationId(), is ("123456"));
        assertThat(retrievedEntity.getAgencyLocationId(), is("123456"));
        assertThat(retrievedEntity.getExpiryDateTime(), is(EXPIRY_DATE_TIME));
        assertThat(retrievedEntity.getDeallocationReason(), is("Unknown"));

        assertThat(retrievedEntity.getCreateUpdate(), is(equalTo(transientEntity.getCreateUpdate())));
    }

    @Test
    public void givenAPersistentInstanceThenNullableValuesAreUpdateable() {

        val entity = repository.save(transientEntity());
        TestTransaction.flagForCommit();
        TestTransaction.end();

        TestTransaction.start();
        val retrievedEntity = repository.findOne(entity.getOffenderKeyworkerId());

        assertThat(retrievedEntity.getCreateUpdate().getModifyDateTime(), nullValue());
        assertThat(retrievedEntity.getCreateUpdate().getModifyUserId(), nullValue());

        retrievedEntity.setCreateUpdate((addUpdateInfo(retrievedEntity.getCreateUpdate())));

        TestTransaction.flagForCommit();
        TestTransaction.end();

        TestTransaction.start();

        val persistedUpdates = repository.findOne(entity.getOffenderKeyworkerId());

        assertThat(persistedUpdates.getCreateUpdate().getModifyDateTime(), notNullValue());
        assertThat(persistedUpdates.getCreateUpdate().getModifyUserId(), is("Modify User Id"));
    }

    private OffenderKeyworker transientEntity() {
        return OffenderKeyworker
                .builder()
                .offenderNo("A1234AA")
                .staffId(nextId())
                .assignedDateTime(ASSIGNED_DATE_TIME)
                .active(true)
                .allocationReason("NoIdea")
                .allocationType(AllocationType.A)
                .userId("The Assigning User")
                .agencyLocationId("123456")
                .expiryDateTime(EXPIRY_DATE_TIME)
                .deallocationReason("Unknown")
                .createUpdate(creationTimeInfo())
                .build();
    }

    private CreateUpdate creationTimeInfo() {
        return CreateUpdate
                .builder()
                .creationDateTime(LocalDateTime.now())
                .createUserId("Creation User Id")
                .build();
    }

    private CreateUpdate addUpdateInfo(CreateUpdate createUpdate) {
        return createUpdate
                .toBuilder()
                .modifyDateTime(LocalDateTime.now())
                .modifyUserId("Modify User Id")
                .build();
    }

    private static long nextId() {
        return currentId++;
    }
}
