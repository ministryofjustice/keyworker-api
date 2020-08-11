package uk.gov.justice.digital.hmpps.keyworker.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import uk.gov.justice.digital.hmpps.keyworker.dto.KeyworkerDto;
import uk.gov.justice.digital.hmpps.keyworker.dto.Prison;
import uk.gov.justice.digital.hmpps.keyworker.exception.AllocationException;
import uk.gov.justice.digital.hmpps.keyworker.model.AllocationType;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.SortedSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.justice.digital.hmpps.keyworker.services.KeyworkerTestHelper.CAPACITY_TIER_1;
import static uk.gov.justice.digital.hmpps.keyworker.services.KeyworkerTestHelper.CAPACITY_TIER_2;
import static uk.gov.justice.digital.hmpps.keyworker.services.KeyworkerTestHelper.FULLY_ALLOCATED;
import static uk.gov.justice.digital.hmpps.keyworker.services.KeyworkerTestHelper.getKeyworker;
import static uk.gov.justice.digital.hmpps.keyworker.services.KeyworkerTestHelper.getKeyworkers;
import static uk.gov.justice.digital.hmpps.keyworker.services.KeyworkerTestHelper.getPreviousKeyworkerAutoAllocation;
import static uk.gov.justice.digital.hmpps.keyworker.services.KeyworkerTestHelper.initKeyworkerPool;
import static uk.gov.justice.digital.hmpps.keyworker.services.KeyworkerTestHelper.mockPrisonerAllocationHistory;

/**
 * Unit test for Key worker pool.
 */
@ExtendWith(MockitoExtension.class)
class KeyworkerPoolTest {
    private static final String TEST_AGENCY_ID = "LEI";

    private KeyworkerPool keyworkerPool;

    @Mock
    private KeyworkerService keyworkerService;
    @Mock
    private PrisonSupportedService prisonSupportedService;

    @BeforeEach
    void setUp() {
        final var prisonDetail = Prison.builder()
                .prisonId(TEST_AGENCY_ID).capacityTier1(CAPACITY_TIER_1).capacityTier2(CAPACITY_TIER_2)
                .build();
        when(prisonSupportedService.getPrisonDetail(TEST_AGENCY_ID)).thenReturn(prisonDetail);
    }

    // Each unit test below is preceded by acceptance criteria in Given-When-Then form
    // KW = Key worker
    // KWP = Key worker pool
    // Capacity can refer to spare allocation capacity (i.e. the KW has capacity for further offender allocations)
    //  or total capacity
    // Allocation refers to an extant and active relationship of an offender to a Key worker
    //   (there is a distinction between an automatically created allocation and a manually created allocation)
    // For purposes of these tests, 'multiple' means at least three or more

    // Given an offender is seeking KW allocation
    // And offender has never previously been allocated to a KW
    // And there is a single KW in the KWP
    // And that KW has capacity
    // When KWP requested for KW for offender
    // Then single KW in KWP is returned
    //
    // If this test fails, an offender will not be allocated to a Key worker.
    @Test
    void testSingleKeyworkerWithSpareCapacity() {
        // Single KW, with capacity, in KWP
        final var keyworker = getKeyworker(1, CAPACITY_TIER_1, CAPACITY_TIER_1);
        keyworkerPool = initKeyworkerPool(keyworkerService, prisonSupportedService, Collections.singleton(keyworker), TEST_AGENCY_ID);

        // Request KW from pool for offender
        final var allocatedKeyworker = keyworkerPool.getKeyworker("A1111AA");

        // Verify same KW used to initialise pool is returned
        assertThat(allocatedKeyworker).isSameAs(keyworker);
    }

    // Given an offender is seeking KW allocation
    // And offender has never previously been allocated to a KW
    // And there is a single KW in the KWP
    // And that KW is fully allocated (has no capacity)
    // When KWP requested for KW for offender
    // Then an error is logged with message to effect that all Key workers are fully allocated
    // And an AllocationException is thrown with message to effect that all Key workers are fully allocated
    //
    // If this test fails, a Key worker will be allocated too many offenders.
    @Test
    void testPoolErrorsWhenSingleKeyworkerIsFullyAllocated() {
        // Single KW, fully allocated, in KWP
        final var keyworker = getKeyworker(1, FULLY_ALLOCATED, CAPACITY_TIER_1);
        keyworkerPool = initKeyworkerPool(keyworkerService, prisonSupportedService, Collections.singleton(keyworker), TEST_AGENCY_ID);

        // Request KW from pool (catching expected exception)
        final var thrown = catchThrowable(() -> keyworkerPool.getKeyworker("A1111AA"));

        assertThat(thrown)
                .isInstanceOf(AllocationException.class)
                .hasMessage(KeyworkerPool.OUTCOME_ALL_KEY_WORKERS_AT_CAPACITY);
    }

    // Given an offender is seeking KW allocation
    // And offender has never previously been allocated to a KW
    // And there are multiple KWs in the KWP
    // And all KWs have differing allocation numbers (but none are fully allocated)
    // When KWP requested for KW offender
    // Then KW with most capacity is returned
    //
    // If this test fails, offenders may not be allocated evenly across Key workers.
    @Test
    void testKeyworkerWithMostSpareCapacityIsReturned() {
        // Multiple KWs, all with capacity, in KWP
        final var lowAllocCount = 1;
        final var highAllocCount = FULLY_ALLOCATED - 1;
        final var keyworkers = getKeyworkers(3, lowAllocCount, highAllocCount, CAPACITY_TIER_1);
        keyworkerPool = initKeyworkerPool(keyworkerService, prisonSupportedService, keyworkers, TEST_AGENCY_ID);

        // Request KW from pool for offender
        final var allocatedKeyworker = keyworkerPool.getKeyworker("A1111AA");

        // Verify returned KW is the one with fewest allocations
        final var fewestAllocs = keyworkers.stream().mapToInt(KeyworkerDto::getNumberAllocated).min();

        assertThat(allocatedKeyworker.getNumberAllocated()).isEqualTo(fewestAllocs.orElse(-1));
    }

    // Given an offender is seeking KW allocation
    // And offender has never previously been allocated to a KW
    // And there are multiple KWs in the KWP
    // And one KW is fully allocated but with a lower capacity and the fewest allocations
    // When KWP requested for KW offender
    // Then the KW with lower capacity is NOT returned
    //
    // If this test fails, offenders may be wrongly allocated to a PT or otherwise low-capacity Key worker.
    @Test
    void testKeyworkerWithLowerCapacityIsNotReturned() {
        // Multiple KWs, all with capacity, in KWP
        final var lowAllocCount = 6;
        final var highAllocCount = FULLY_ALLOCATED - 1;
        final var keyworkers = getKeyworkers(3, lowAllocCount, highAllocCount, CAPACITY_TIER_1);
        keyworkers.get(0).setCapacity(3);
        keyworkers.get(0).setNumberAllocated(4);
        keyworkerPool = initKeyworkerPool(keyworkerService, prisonSupportedService, keyworkers, TEST_AGENCY_ID);

        // Request KW from pool for offender
        final var allocatedKeyworker = keyworkerPool.getKeyworker("A1111AA");

        // Verify the low-capacity KW was not chosen despite having the fewest allocations
        assertThat(allocatedKeyworker.getStaffId()).isNotEqualTo(keyworkers.get(0).getStaffId());
    }

    // Check the staffid last resort ordering
    @Test
    void testKeyworkerOrderAllIdentical() {
        final var keyworkers = getKeyworkers(5, 1, 1, CAPACITY_TIER_1);
        // Make life difficult for the comparator - decreasing staff id order
        Collections.shuffle(keyworkers);
        keyworkerPool = initKeyworkerPool(keyworkerService, prisonSupportedService, keyworkers, TEST_AGENCY_ID);

        // Get KW sorted set to check directly
        final var set = (SortedSet<KeyworkerDto>) ReflectionTestUtils.getField(keyworkerPool, "keyworkerPool");
        final var it = set.iterator();
        assertThat(it.next().getStaffId()).isEqualTo(1L);
        assertThat(it.next().getStaffId()).isEqualTo(2L);
        assertThat(it.next().getStaffId()).isEqualTo(3L);
        assertThat(it.next().getStaffId()).isEqualTo(4L);
        assertThat(it.next().getStaffId()).isEqualTo(5L);
    }

    // Given an offender is seeking KW allocation
    // And offender has never previously been allocated to a KW
    // And there are multiple KWs in the KWP
    // And multiple KWs have most capacity (i.e. same number of allocations and more allocations than other KWs)
    // And all KWs have existing auto-allocations assigned at different date/times
    // When KWP requested for KW offender
    // Then KW with most capacity and least recent auto-allocation is returned
    //
    // If this test fails, offenders may not be allocated to Key worker with most capacity and least-recent auto-allocation.
    @Test
    void testKeyworkerWithMostCapacityAndLeastRecentAllocationIsReturned() {
        // Multiple KWs, all with capacity and a couple with same least number of allocations, in KWP
        final var lowAllocCount = 1;
        final var highAllocCount = FULLY_ALLOCATED - 1;
        final var staffId1 = 1L;
        final var staffId2 = 2L;
        final var staffId3 = 3L;

        final var keyworkers = List.of(
                getKeyworker(1, lowAllocCount, CAPACITY_TIER_1),
                getKeyworker(2, highAllocCount, CAPACITY_TIER_1),
                getKeyworker(3, lowAllocCount, CAPACITY_TIER_1));

        keyworkerPool = initKeyworkerPool(keyworkerService, prisonSupportedService, keyworkers, TEST_AGENCY_ID);

        // Some previous allocations for each Key worker
        final var refDateTime = LocalDateTime.now();

        final var staff1Allocation =
                getPreviousKeyworkerAutoAllocation(TEST_AGENCY_ID, "A5555AA", staffId1, refDateTime.minusDays(2));

        final var staff2Allocation =
                getPreviousKeyworkerAutoAllocation(TEST_AGENCY_ID, "A7777AA", staffId2, refDateTime.minusDays(7));

        final var staff3Allocation =
                getPreviousKeyworkerAutoAllocation(TEST_AGENCY_ID, "A3333AA", staffId3, refDateTime.minusDays(5));
        final var staff3IrrelevantAllocationP =
                getPreviousKeyworkerAutoAllocation(TEST_AGENCY_ID, "A3333AB", staffId3, refDateTime.minusDays(1));
        final var staff3IrrelevantAllocationM =
                getPreviousKeyworkerAutoAllocation(TEST_AGENCY_ID, "A3333AC", staffId3, refDateTime.minusDays(1));
        staff3IrrelevantAllocationP.setAllocationType(AllocationType.PROVISIONAL);
        staff3IrrelevantAllocationM.setAllocationType(AllocationType.MANUAL);

        when(keyworkerService.getAllocationsForKeyworker(eq(staffId1))).thenReturn(Collections.singletonList(staff1Allocation));
        when(keyworkerService.getAllocationsForKeyworker(eq(staffId3))).thenReturn(List.of(
                staff3Allocation, staff3IrrelevantAllocationP, staff3IrrelevantAllocationM));

        // Request KW from pool for offender
        final var allocatedKeyworker = keyworkerPool.getKeyworker("A1111AA");

        // Verify collaborators
        verify(keyworkerService, Mockito.times(2)).getAllocationsForKeyworker(anyLong());

        // Verify returned KW is the one with fewest allocations and least recent auto-allocation
        assertThat(allocatedKeyworker.getStaffId()).isEqualTo(staffId3);
    }

    // Given an offender is seeking KW allocation
    // And there are multiple KWs in the KWP
    // And offender has been previously allocated to one of the KWs in KWP
    // When KWP requested for KW for offender
    // Then KW that offender was previously allocated to is returned
    //
    // If this test fails, an offender will not be allocated to a Key worker they have previously been allocated to.
    @Test
    void testPreviouslyAllocatedKeyworkerIsReturned() {
        // Multiple KWs, all with capacity, in KWP
        final var lowAllocCount = 1;
        final var highAllocCount = FULLY_ALLOCATED - 1;
        final var allocOffenderNo = "A1111AA";
        final long allocStaffId = 2;

        final var keyworkers = getKeyworkers(3, lowAllocCount, highAllocCount, CAPACITY_TIER_1);
        keyworkerPool = initKeyworkerPool(keyworkerService, prisonSupportedService, keyworkers, TEST_AGENCY_ID);

        // A previous allocation between the unallocated offender and Key worker with staffId = 2
        mockPrisonerAllocationHistory(keyworkerService,
                getPreviousKeyworkerAutoAllocation(TEST_AGENCY_ID, allocOffenderNo, allocStaffId));

        // Request KW from pool for offender
        final var allocatedKeyworker = keyworkerPool.getKeyworker(allocOffenderNo);

        // Verify that returned KW is one to whom offender was previously allocated
        assertThat(allocatedKeyworker.getStaffId()).isEqualTo(allocStaffId);
    }

    // Given an offender is seeking KW allocation
    // And there are multiple KWs in the KWP
    // And offender has been previously allocated, at different times, to several of the KWs in KWP
    // When KWP requested for KW for offender
    // Then KW that offender was most recently previously allocated to is returned
    //
    // If this test fails, an offender will not be allocated to the Key worker they were most recently allocated to.
    @Test
    void testMostRecentPreviouslyAllocatedKeyworkerIsReturned() {
        // Multiple KWs, all with capacity, in KWP
        final var lowAllocCount = 1;
        final var highAllocCount = FULLY_ALLOCATED - 1;
        final var allocOffenderNo = "A1111AA";
        final long allocStaffIdMostRecent = 4;
        final long allocStaffIdOther = 3;
        final long allocStaffIdLeastRecent = 2;
        final var ldtMostRecent = LocalDateTime.now().minusDays(7);
        final var ldtOther = ldtMostRecent.minusDays(7);
        final var ldtLeastRecent = ldtOther.minusDays(7);

        final var keyworkers = getKeyworkers(7, lowAllocCount, highAllocCount, CAPACITY_TIER_1);
        keyworkerPool = initKeyworkerPool(keyworkerService, prisonSupportedService, keyworkers, TEST_AGENCY_ID);

        // Previous allocations between the unallocated offender and previous KWs
        mockPrisonerAllocationHistory(keyworkerService,
                getPreviousKeyworkerAutoAllocation(TEST_AGENCY_ID, allocOffenderNo, allocStaffIdMostRecent, ldtMostRecent),
                getPreviousKeyworkerAutoAllocation(TEST_AGENCY_ID, allocOffenderNo, allocStaffIdOther, ldtOther),
                getPreviousKeyworkerAutoAllocation(TEST_AGENCY_ID, allocOffenderNo, allocStaffIdLeastRecent, ldtLeastRecent));

        // Request KW from pool for offender
        final var allocatedKeyworker = keyworkerPool.getKeyworker(allocOffenderNo);

        // Verify that returned KW is one to whom offender was most recently previously allocated
        assertThat(allocatedKeyworker.getStaffId()).isEqualTo(allocStaffIdMostRecent);
    }

    // Given a KW is not in the KWP
    // When attempt is made to refresh KW in the KWP
    // Then an IllegalStateException is thrown because the KW is not in the KWP
    //
    // If this test fails, a KW who is not a member of the KWP may be added to the KWP when they shouldn't be
    @Test
    void testExceptionThrownWhenKeyworkerRefreshedButNotMemberOfKeyworkerPool() {
        // KWP initialised with an initial set of KWs
        final var lowAllocCount = 1;
        final var highAllocCount = FULLY_ALLOCATED - 1;

        final var keyworkers = getKeyworkers(7, lowAllocCount, highAllocCount, CAPACITY_TIER_1);
        keyworkerPool = initKeyworkerPool(keyworkerService, prisonSupportedService, keyworkers, TEST_AGENCY_ID);

        // A KW who is not a member of KWP
        final var otherKeyworker = getKeyworker(8, 5, CAPACITY_TIER_1);

        // Attempt refresh
        assertThatThrownBy(() -> keyworkerPool.incrementAndRefreshKeyworker(otherKeyworker)).isInstanceOf(IllegalStateException.class);
    }

    // Given a KW is in the KWP
    // When attempt is made to increment the KW's allocations
    // Then attempt is successful and KWP is updated with KW in new position in pool
    //
    // If this test fails, a KW who is a member of the KWP may not be refreshed correctly in KWP and this may result in
    // incorrect allocations taking place for the KW due to KWP having an out-of-date KW entry.
    @Test
    void testKeyworkerRefreshedWhenMemberOfKeyworkerPool() {
        // KWP initialised with an initial set of KWs
        final var lowAllocCount = 1;
        final var highAllocCount = FULLY_ALLOCATED - 1;
        final long refreshKeyworkerStaffId = 27;

        final var keyworkers = getKeyworkers(5, lowAllocCount, highAllocCount, CAPACITY_TIER_1);

        // Add another couple of KWs with known allocation counts (one high, one low)
        final var firstKeyworker = getKeyworker(refreshKeyworkerStaffId - 1, 0, CAPACITY_TIER_1);
        final var secondKeyworker = getKeyworker(refreshKeyworkerStaffId, 0, CAPACITY_TIER_1);

        keyworkers.add(firstKeyworker);
        keyworkers.add(secondKeyworker);

        keyworkerPool = initKeyworkerPool(keyworkerService, prisonSupportedService, keyworkers, TEST_AGENCY_ID);

        // Verify that priority KW is the one with known low alloc count and lowest staff id
        var priorityKeyworker = keyworkerPool.getKeyworker("A1111AA");

        assertThat(priorityKeyworker).isSameAs(firstKeyworker);

        // Attempt refresh
        keyworkerPool.incrementAndRefreshKeyworker(firstKeyworker);

        // Verify that priority KW is now the second one (that still has zero allocations)
        priorityKeyworker = keyworkerPool.getKeyworker("A2222AA");

        assertThat(priorityKeyworker).isSameAs(secondKeyworker);
    }
}
