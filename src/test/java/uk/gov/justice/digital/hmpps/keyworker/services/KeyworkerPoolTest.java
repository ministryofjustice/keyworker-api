package uk.gov.justice.digital.hmpps.keyworker.services;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;
import uk.gov.justice.digital.hmpps.keyworker.dto.KeyworkerDto;
import uk.gov.justice.digital.hmpps.keyworker.dto.PrisonerDetailDto;
import uk.gov.justice.digital.hmpps.keyworker.exception.AllocationException;
import uk.gov.justice.digital.hmpps.keyworker.model.OffenderKeyworker;
import uk.gov.justice.digital.hmpps.keyworker.repository.OffenderKeyworkerRepository;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;
import static uk.gov.justice.digital.hmpps.keyworker.services.KeyworkerTestHelper.*;

/**
 * Unit test for Key worker pool.
 */
@RunWith(MockitoJUnitRunner.class)
public class KeyworkerPoolTest {
    private static final String TEST_AGENCY_ID = "LEI";
    private static final long STAFF_ID = 1L;
    private static final String OFFENDER_NO = "AA1234X";

    private KeyworkerPool keyworkerPool;
    private Set<Integer> capacityTiers;

    @Mock
    private KeyworkerService keyworkerService;

    @Mock
    private OffenderKeyworkerRepository offenderKeyworkerRepository;

    @Mock
    private NomisService nomisService;

    @Before
    public void setUp() {
        // Initialise Key worker allocation capacity tiers
        capacityTiers = new TreeSet<>();

        capacityTiers.add(CAPACITY_TIER_1);
        capacityTiers.add(CAPACITY_TIER_2);
    }

    // Each unit test below is preceded by acceptance criteria in Given-When-Then form
    // KW = Key worker
    // KWP = Key worker pool
    // Capacity refers to spare allocation capacity (i.e. the KW has capacity for further offender allocations)
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
    public void testSingleKeyworkerWithSpareCapacity() {
        // Single KW, with capacity, in KWP
        KeyworkerDto keyworker = getKeyworker(1, CAPACITY_TIER_1, CAPACITY_TIER_1);
        keyworkerPool = initKeyworkerPool(keyworkerService, offenderKeyworkerRepository, nomisService, Collections.singleton(keyworker), capacityTiers);

        // Request KW from pool for offender
        KeyworkerDto allocatedKeyworker = keyworkerPool.getKeyworker("A1111AA");

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
    public void testPoolErrorsWhenSingleKeyworkerIsFullyAllocated() {
        // Single KW, fully allocated, in KWP
        KeyworkerDto keyworker = getKeyworker(1, FULLY_ALLOCATED, CAPACITY_TIER_1);
        keyworkerPool = initKeyworkerPool(keyworkerService, offenderKeyworkerRepository, nomisService, Collections.singleton(keyworker), capacityTiers);

        // Request KW from pool (catching expected exception)
        Throwable thrown = catchThrowable(() -> keyworkerPool.getKeyworker("A1111AA"));

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
    public void testKeyworkerWithMostSpareCapacityIsReturned() {
        // Multiple KWs, all with capacity, in KWP
        final int lowAllocCount = 1;
        final int highAllocCount = FULLY_ALLOCATED - 1;
        List<KeyworkerDto> keyworkers = getKeyworkers(3, lowAllocCount, highAllocCount, CAPACITY_TIER_1);
        keyworkerPool = initKeyworkerPool(keyworkerService, offenderKeyworkerRepository, nomisService, keyworkers, capacityTiers);

        // Request KW from pool for offender
        KeyworkerDto allocatedKeyworker = keyworkerPool.getKeyworker("A1111AA");

        // Verify returned KW is the one with fewest allocations
        OptionalInt fewestAllocs = keyworkers.stream().mapToInt(KeyworkerDto::getNumberAllocated).min();

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
    public void testKeyworkerWithLowerCapacityIsNotReturned() {
        // Multiple KWs, all with capacity, in KWP
        final int lowAllocCount = 6;
        final int highAllocCount = FULLY_ALLOCATED - 1;
        List<KeyworkerDto> keyworkers = getKeyworkers(3, lowAllocCount, highAllocCount, CAPACITY_TIER_1);
        keyworkers.get(0).setCapacity(3);
        keyworkers.get(0).setNumberAllocated(4);
        keyworkerPool = initKeyworkerPool(keyworkerService, offenderKeyworkerRepository, nomisService, keyworkers, capacityTiers);

        // Request KW from pool for offender
        KeyworkerDto allocatedKeyworker = keyworkerPool.getKeyworker("A1111AA");

        // Verify the low-capacity KW was not chosen despite having the fewest allocations
        assertThat(allocatedKeyworker.getStaffId()).isNotEqualTo(keyworkers.get(0).getStaffId());
    }

    // Check the staffid last resort ordering
    @Test
    public void testKeyworkerOrderAllIdentical() {
        List<KeyworkerDto> keyworkers = getKeyworkers(5, 1, 1, CAPACITY_TIER_1);
        // Make life difficult for the comparator - decreasing staff id order
        Collections.shuffle(keyworkers);
        keyworkerPool = initKeyworkerPool(keyworkerService, offenderKeyworkerRepository, nomisService, keyworkers, capacityTiers);

        // Get KW sorted set to check directly
        SortedSet<KeyworkerDto> set = (SortedSet<KeyworkerDto>) ReflectionTestUtils.getField(keyworkerPool, "keyworkerPool");
        Iterator<KeyworkerDto> it = set.iterator();
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
    public void testKeyworkerWithMostCapacityAndLeastRecentAllocationIsReturned() {
        // Multiple KWs, all with capacity and a couple with same least number of allocations, in KWP
        final int lowAllocCount = 1;
        final int highAllocCount = FULLY_ALLOCATED - 1;
        final long staffId1 = 1L;
        final long staffId2 = 2L;
        final long staffId3 = 3L;

        List<KeyworkerDto> keyworkers = Arrays.asList(
                getKeyworker(1, lowAllocCount, CAPACITY_TIER_1),
                getKeyworker(2, highAllocCount, CAPACITY_TIER_1),
                getKeyworker(3, lowAllocCount, CAPACITY_TIER_1));

        keyworkerPool = initKeyworkerPool(keyworkerService, offenderKeyworkerRepository, nomisService, keyworkers, capacityTiers);

        // Some previous allocations for each Key worker
        LocalDateTime refDateTime = LocalDateTime.now();

        OffenderKeyworker staff1Allocation =
                getPreviousKeyworkerAutoAllocation(TEST_AGENCY_ID, "A5555AA", staffId1, refDateTime.minusDays(2));

        OffenderKeyworker staff2Allocation =
                getPreviousKeyworkerAutoAllocation(TEST_AGENCY_ID, "A7777AA", staffId2, refDateTime.minusDays(7));

        OffenderKeyworker staff3Allocation =
                getPreviousKeyworkerAutoAllocation(TEST_AGENCY_ID, "A3333AA", staffId3, refDateTime.minusDays(5));

        when(keyworkerService.getAllocationsForKeyworker(eq(staffId1))).thenReturn(Collections.singletonList(staff1Allocation));
        when(keyworkerService.getAllocationsForKeyworker(eq(staffId2))).thenReturn(Collections.singletonList(staff2Allocation));
        when(keyworkerService.getAllocationsForKeyworker(eq(staffId3))).thenReturn(Collections.singletonList(staff3Allocation));

        // Request KW from pool for offender
        KeyworkerDto allocatedKeyworker = keyworkerPool.getKeyworker("A1111AA");

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
    public void testPreviouslyAllocatedKeyworkerIsReturned() {
        // Multiple KWs, all with capacity, in KWP
        final int lowAllocCount = 1;
        final int highAllocCount = FULLY_ALLOCATED - 1;
        final String allocOffenderNo = "A1111AA";
        final long allocStaffId = 2;

        List<KeyworkerDto> keyworkers = getKeyworkers(3, lowAllocCount, highAllocCount, CAPACITY_TIER_1);
        keyworkerPool = initKeyworkerPool(keyworkerService, offenderKeyworkerRepository, nomisService, keyworkers, capacityTiers);

        // A previous allocation between the unallocated offender and Key worker with staffId = 2
        mockPrisonerAllocationHistory(keyworkerService,
                getPreviousKeyworkerAutoAllocation(TEST_AGENCY_ID, allocOffenderNo, allocStaffId));

        // Request KW from pool for offender
        KeyworkerDto allocatedKeyworker = keyworkerPool.getKeyworker(allocOffenderNo);

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
    public void testMostRecentPreviouslyAllocatedKeyworkerIsReturned() {
        // Multiple KWs, all with capacity, in KWP
        final int lowAllocCount = 1;
        final int highAllocCount = FULLY_ALLOCATED - 1;
        final String allocOffenderNo = "A1111AA";
        final long allocStaffIdMostRecent = 4;
        final long allocStaffIdOther = 3;
        final long allocStaffIdLeastRecent = 2;
        final LocalDateTime ldtMostRecent = LocalDateTime.now().minusDays(7);
        final LocalDateTime ldtOther = ldtMostRecent.minusDays(7);
        final LocalDateTime ldtLeastRecent = ldtOther.minusDays(7);

        List<KeyworkerDto> keyworkers = getKeyworkers(7, lowAllocCount, highAllocCount, CAPACITY_TIER_1);
        keyworkerPool = initKeyworkerPool(keyworkerService, offenderKeyworkerRepository, nomisService, keyworkers, capacityTiers);

        // Previous allocations between the unallocated offender and previous KWs
        mockPrisonerAllocationHistory(keyworkerService,
                getPreviousKeyworkerAutoAllocation(TEST_AGENCY_ID, allocOffenderNo, allocStaffIdMostRecent, ldtMostRecent),
                getPreviousKeyworkerAutoAllocation(TEST_AGENCY_ID, allocOffenderNo, allocStaffIdOther, ldtOther),
                getPreviousKeyworkerAutoAllocation(TEST_AGENCY_ID, allocOffenderNo, allocStaffIdLeastRecent, ldtLeastRecent));

        // Request KW from pool for offender
        KeyworkerDto allocatedKeyworker = keyworkerPool.getKeyworker(allocOffenderNo);

        // Verify that returned KW is one to whom offender was most recently previously allocated
        assertThat(allocatedKeyworker.getStaffId()).isEqualTo(allocStaffIdMostRecent);
    }

    // Given a KW is not in the KWP
    // When attempt is made to refresh KW in the KWP
    // Then an IllegalStateException is thrown because the KW is not in the KWP
    //
    // If this test fails, a KW who is not a member of the KWP may be added to the KWP when they shouldn't be
    @Test(expected = IllegalStateException.class)
    public void testExceptionThrownWhenKeyworkerRefreshedButNotMemberOfKeyworkerPool() {
        // KWP initialised with an initial set of KWs
        final int lowAllocCount = 1;
        final int highAllocCount = FULLY_ALLOCATED - 1;

        List<KeyworkerDto> keyworkers = getKeyworkers(7, lowAllocCount, highAllocCount, CAPACITY_TIER_1);
        keyworkerPool = initKeyworkerPool(keyworkerService, offenderKeyworkerRepository, nomisService, keyworkers, capacityTiers);

        // A KW who is not a member of KWP
        KeyworkerDto otherKeyworker = getKeyworker(8, 5, CAPACITY_TIER_1);

        // Attempt refresh
        keyworkerPool.incrementAndRefreshKeyworker(otherKeyworker);
    }

    // Given a KW is in the KWP
    // When attempt is made to increment the KW's allocations
    // Then attempt is successful and KWP is updated with KW in new position in pool
    //
    // If this test fails, a KW who is a member of the KWP may not be refreshed correctly in KWP and this may result in
    // incorrect allocations taking place for the KW due to KWP having an out-of-date KW entry.
    @Test
    public void testKeyworkerRefreshedWhenMemberOfKeyworkerPool() {
        // KWP initialised with an initial set of KWs
        final int lowAllocCount = 1;
        final int highAllocCount = FULLY_ALLOCATED - 1;
        final long refreshKeyworkerStaffId = 27;

        List<KeyworkerDto> keyworkers = getKeyworkers(5, lowAllocCount, highAllocCount, CAPACITY_TIER_1);

        // Add another couple of KWs with known allocation counts (one high, one low)
        KeyworkerDto firstKeyworker = getKeyworker(refreshKeyworkerStaffId - 1, 0, CAPACITY_TIER_1);
        KeyworkerDto secondKeyworker = getKeyworker(refreshKeyworkerStaffId, 0, CAPACITY_TIER_1);

        keyworkers.add(firstKeyworker);
        keyworkers.add(secondKeyworker);

        keyworkerPool = initKeyworkerPool(keyworkerService, offenderKeyworkerRepository, nomisService, keyworkers, capacityTiers);

        // Verify that priority KW is the one with known low alloc count and lowest staff id
        KeyworkerDto priorityKeyworker = keyworkerPool.getKeyworker("A1111AA");

        assertThat(priorityKeyworker).isSameAs(firstKeyworker);

        // Attempt refresh
        keyworkerPool.incrementAndRefreshKeyworker(firstKeyworker);

        // Verify that priority KW is now the second one (that still has zero allocations)
        priorityKeyworker = keyworkerPool.getKeyworker("A2222AA");

        assertThat(priorityKeyworker).isSameAs(secondKeyworker);
    }

    // Given offender X is released
    // And their allocation to Key worker A is expired on release
    // And it is less than 48 hours since release of offender X
    // And offender X has not been re-admitted to any agency
    // When details are requested for Key worker A
    // Then number of allocations for Key worker A includes all active allocations and expired allocation to offender X
    //
    // If this test fails, a Key worker may appear to have more capacity than they should and may be allocated
    // offenders when they normally would not have been.
    @Test
    public void testGetRecentDeallocationsWhenAllocationRecentlyExpiredDueToOffenderRelease() {

        List<KeyworkerDto> keyworkers = getKeyworkers(3, 0, 2, CAPACITY_TIER_1, TEST_AGENCY_ID);

        // An allocation for an offender that expired only 2 hours ago
        OffenderKeyworker expiredAllocation = getPreviousKeyworkerAutoAllocation(TEST_AGENCY_ID, OFFENDER_NO, STAFF_ID, LocalDateTime.now().minusDays(7));
        expiredAllocation = expireAllocation(expiredAllocation, null, LocalDateTime.now().minusHours(2));
        List<OffenderKeyworker> kwAllocations = Arrays.asList(getPreviousKeyworkerAutoAllocation(TEST_AGENCY_ID, "OTHER_OFFENDER", STAFF_ID),
                expiredAllocation);
        when(offenderKeyworkerRepository.findByStaffIdAndPrisonId(eq(STAFF_ID), eq(TEST_AGENCY_ID))).thenReturn(kwAllocations);

        final PrisonerDetailDto summary = PrisonerDetailDto.builder().offenderNo(OFFENDER_NO).currentlyInPrison("N").latestLocationId(TEST_AGENCY_ID).build();
        when(nomisService.getOffender(OFFENDER_NO)).thenReturn(Collections.singletonList(summary));

        // test deallocation count
        keyworkerPool = initKeyworkerPool(keyworkerService, offenderKeyworkerRepository, nomisService, keyworkers, capacityTiers);

        Map deallocatedMap = (Map)ReflectionTestUtils.getField(keyworkerPool, "keyworkerRecentlyDeallocatedNumber");
        // Assertions - number of allocations for STAFF_ID should include the recently expired allocation
        //              because offender who is subject of recently expired allocation does not have an active booking
        //              in any agency (either same or different)
        assertThat(deallocatedMap.get(STAFF_ID)).isEqualTo(1);
    }

    // Given offender X is released
    // And their allocation to Key worker A is expired
    // And it is 48 hours or more since release of offender X
    // When details are requested for Key worker A
    // Then number of allocations for Key worker A includes active allocations only
    //
    // If this test fails, a Key worker may appear to have less capacity than they actually do and may not be allocated
    // offenders when they normally would have been.
    @Test
    public void testGetRecentDeallocationsWhenAllocationExpiredDueToOffenderRelease() {
        List<KeyworkerDto> keyworkers = getKeyworkers(3, 0, 2, CAPACITY_TIER_1, TEST_AGENCY_ID);

        // Expectations
        //  - repo to return only active allocations
        List<OffenderKeyworker> kwAllocations = Arrays.asList(getPreviousKeyworkerAutoAllocation(TEST_AGENCY_ID, "OTHER_OFFENDER", STAFF_ID));
        when(offenderKeyworkerRepository.findByStaffIdAndPrisonId(eq(STAFF_ID), eq(TEST_AGENCY_ID))).thenReturn(kwAllocations);

        // test deallocations
        keyworkerPool = initKeyworkerPool(keyworkerService, offenderKeyworkerRepository, nomisService, keyworkers, capacityTiers);

        Map deallocatedMap = (Map)ReflectionTestUtils.getField(keyworkerPool, "keyworkerRecentlyDeallocatedNumber");
        // Assertions - number of allocations should not include the recently expired allocation
        //              because allocation expired too long ago
        assertThat(deallocatedMap.get(STAFF_ID)).isEqualTo(0);
        verify(offenderKeyworkerRepository, atLeastOnce()).findByStaffIdAndPrisonId(eq(STAFF_ID), eq(TEST_AGENCY_ID));
    }

    // Given offender X is released
    // And their allocation to Key worker A is expired
    // And it is less than 48 hours since release of offender X
    // And offender X has been re-allocated to same Key worker in same agency
    // When details are requested for Key worker A
    // Then number of allocations for Key worker A includes active allocations only
    //
    // If this test fails, the allocation of an offender to a Key worker may be counted twice.
    @Test
    public void testGetRecentDeallocationsWhenAllocationRecentlyExpiredAndSameOffenderReallocated() {
        List<KeyworkerDto> keyworkers = getKeyworkers(3, 0, 2, CAPACITY_TIER_1, TEST_AGENCY_ID);

        // A set of allocations for KW including an allocation for an offender that expired one day ago and including
        // an active allocation for same offender that was assigned two hours ago.
        OffenderKeyworker expiredAllocation = getPreviousKeyworkerAutoAllocation(TEST_AGENCY_ID, OFFENDER_NO, STAFF_ID, LocalDateTime.now().minusDays(5));
        expiredAllocation = expireAllocation(expiredAllocation, null, LocalDateTime.now().minusDays(1));
        List<OffenderKeyworker> kwAllocations = Arrays.asList(
                expiredAllocation,
                getPreviousKeyworkerAutoAllocation(TEST_AGENCY_ID, OFFENDER_NO, STAFF_ID, LocalDateTime.now().minusHours(2)));

        when(offenderKeyworkerRepository.findByStaffIdAndPrisonId(eq(STAFF_ID), eq(TEST_AGENCY_ID))).thenReturn(kwAllocations);

        final PrisonerDetailDto summary = PrisonerDetailDto.builder().offenderNo(OFFENDER_NO).currentlyInPrison("N").latestLocationId(TEST_AGENCY_ID).build();
        when(nomisService.getOffender(OFFENDER_NO)).thenReturn(Collections.singletonList(summary));

        final OffenderKeyworker allocation = OffenderKeyworker.builder().prisonId(TEST_AGENCY_ID).build();
        when(offenderKeyworkerRepository.findByActiveAndOffenderNo(true, OFFENDER_NO)).thenReturn(Collections.singletonList(allocation));

        // test deallocation count
        keyworkerPool = initKeyworkerPool(keyworkerService, offenderKeyworkerRepository, nomisService, keyworkers, capacityTiers);

        Map deallocatedMap = (Map)ReflectionTestUtils.getField(keyworkerPool, "keyworkerRecentlyDeallocatedNumber");
        // Assertions - number of recent deallocations should not include expired allocation because
        //              offender who is subject of expired allocation has been recalled and reallocated to same Key
        //              worker
        assertThat(deallocatedMap.get(STAFF_ID)).isEqualTo(0);
        verify(offenderKeyworkerRepository, never()).findByActiveAndOffenderNo(true, OFFENDER_NO);
        verify(nomisService, never()).getOffender(OFFENDER_NO);
    }

    // Given offender X is released
    // And their allocation to Key worker A is expired
    // And it is less than 48 hours since release of offender X
    // And offender X has an active booking in a different agency
    // When details are requested for Key worker A
    // Then number of allocations for Key worker A includes active allocations only
    //
    // If this test fails, a Key worker may appear to have less capacity than they should and may not be allocated
    // offenders when they normally would have been.
    @Test
    public void testGetRecentDeallocationsWhenAllocationRecentlyExpiredButOffenderActiveElsewhere() {

        List<KeyworkerDto> keyworkers = getKeyworkers(3, 0, 2, CAPACITY_TIER_1, TEST_AGENCY_ID);

        // An allocation for an offender that expired only 5 hours ago
        OffenderKeyworker expiredAllocation = getPreviousKeyworkerAutoAllocation(TEST_AGENCY_ID, OFFENDER_NO, STAFF_ID, LocalDateTime.now().minusDays(5));
        expiredAllocation = expireAllocation(expiredAllocation, null, LocalDateTime.now().minusHours(5));
        List<OffenderKeyworker> kwAllocations = Arrays.asList(expiredAllocation);

        when(offenderKeyworkerRepository.findByStaffIdAndPrisonId(eq(STAFF_ID), eq(TEST_AGENCY_ID))).thenReturn(kwAllocations);
        // Offender summary for offender who is subject of expired allocation that indicates offender has active booking in different agency
        final OffenderKeyworker allocation = OffenderKeyworker.builder().prisonId("SYI").build();
        when(offenderKeyworkerRepository.findByActiveAndOffenderNo(true, OFFENDER_NO)).thenReturn(Collections.singletonList(allocation));

        final PrisonerDetailDto summary = PrisonerDetailDto.builder().offenderNo(OFFENDER_NO).currentlyInPrison("Y").latestLocationId("SYI").build();
        when(nomisService.getOffender(OFFENDER_NO)).thenReturn(Collections.singletonList(summary));

        // test deallocation count
        keyworkerPool = initKeyworkerPool(keyworkerService, offenderKeyworkerRepository, nomisService, keyworkers, capacityTiers);

        Map deallocatedMap = (Map)ReflectionTestUtils.getField(keyworkerPool, "keyworkerRecentlyDeallocatedNumber");
        // Assertions - number of allocations should include active allocations only, not expired allocation because
        //              offender who is subject of expired allocation now has an active booking at a different agency
        //              and is no longer eligible to be assigned to the same Key worker.
        assertThat(deallocatedMap.get(STAFF_ID)).isEqualTo(0);
        verify(nomisService).getOffender(OFFENDER_NO);
        verify(offenderKeyworkerRepository, never()).findByActiveAndOffenderNo(true, OFFENDER_NO);
    }

    // Given offender X is released
    // And their allocation to Key worker A is expired
    // And it is less than 48 hours since release of offender X
    // And offender X has an active booking in same agency as Key worker A
    // And offender X is unallocated
    // When details are requested for Key worker A
    // Then number of allocations for Key worker A includes all active allocations and expired allocation to offender X
    //
    // If this test fails, a Key worker may appear to have more capacity than they should and may be allocated
    // offenders when they normally would not have been.
    @Test
    public void testGetRecentDeallocationsWhenAllocationRecentlyExpiredAndOffenderActiveButUnallocated() {
        List<KeyworkerDto> keyworkers = getKeyworkers(3, 0, 2, CAPACITY_TIER_1, TEST_AGENCY_ID);

        // An allocation for an offender that expired over a day ago
        OffenderKeyworker expiredAllocation = getPreviousKeyworkerAutoAllocation(TEST_AGENCY_ID, OFFENDER_NO, STAFF_ID, LocalDateTime.now().minusDays(7));
        expiredAllocation = expireAllocation(expiredAllocation, null, LocalDateTime.now().minusHours(30));
        List<OffenderKeyworker> kwAllocations = Arrays.asList(expiredAllocation);

        when(offenderKeyworkerRepository.findByStaffIdAndPrisonId(eq(STAFF_ID), eq(TEST_AGENCY_ID))).thenReturn(kwAllocations);

        // Offender summary for offender who is subject of expired allocation that indicates offender has active booking in same agency
        final PrisonerDetailDto summary = PrisonerDetailDto.builder().offenderNo(OFFENDER_NO).currentlyInPrison("Y").latestLocationId(TEST_AGENCY_ID).build();
        when(nomisService.getOffender(OFFENDER_NO)).thenReturn(Collections.singletonList(summary));

        when(offenderKeyworkerRepository.findByActiveAndOffenderNo(true, OFFENDER_NO)).thenReturn(Collections.emptyList());

        // test deallocation count
        keyworkerPool = initKeyworkerPool(keyworkerService, offenderKeyworkerRepository, nomisService, keyworkers, capacityTiers);

        Map deallocatedMap = (Map)ReflectionTestUtils.getField(keyworkerPool, "keyworkerRecentlyDeallocatedNumber");
        // Assertions - number of allocations should include the recently expired allocation
        //              because offender who is subject of recently expired allocation has an active booking in same
        //              agency but has not yet been allocated to another Key worker
        assertThat(deallocatedMap.get(STAFF_ID)).isEqualTo(1);
        verify(offenderKeyworkerRepository, atLeastOnce()).findByActiveAndOffenderNo(true, OFFENDER_NO);
    }

    // Given offender X is released
    // And their allocation to Key worker A is expired
    // And it is less than 48 hours since release of offender X
    // And offender X has an active booking in same agency as Key worker A
    // And offender X is allocated to a different Key worker
    // When details are requested for Key worker A
    // Then number of allocations for Key worker A includes all active allocations only
    //
    // If this test fails, a Key worker may appear to have less capacity than they should and may not be allocated
    // offenders when they normally would have been.
    @Test
    public void testGetRecentDeallocationsWhenAllocationRecentlyExpiredAndOffenderAllocatedAnotherKeyworker() {
        List<KeyworkerDto> keyworkers = getKeyworkers(3, 0, 2, CAPACITY_TIER_1, TEST_AGENCY_ID);

        // A set of allocations for KW including an allocation for an offender that expired a few hours ago and an
        // active allocation for same offender to different Key worker in same agency that was assigned an hour ago
        OffenderKeyworker expiredAllocation = getPreviousKeyworkerAutoAllocation(TEST_AGENCY_ID, OFFENDER_NO, STAFF_ID, LocalDateTime.now().minusDays(7));
        expiredAllocation = expireAllocation(expiredAllocation, null, LocalDateTime.now().minusHours(9));

        OffenderKeyworker newAllocation = getPreviousKeyworkerAutoAllocation(TEST_AGENCY_ID, OFFENDER_NO, 2L, LocalDateTime.now().minusHours(1));
        List<OffenderKeyworker> kwAllocations = Arrays.asList(expiredAllocation, newAllocation);

        when(offenderKeyworkerRepository.findByStaffIdAndPrisonId(eq(STAFF_ID), eq(TEST_AGENCY_ID))).thenReturn(kwAllocations);

        // Offender summary for offender who is subject of expired allocation that indicates offender has active booking in same agency
        final PrisonerDetailDto summary = PrisonerDetailDto.builder().offenderNo(OFFENDER_NO).currentlyInPrison("Y").latestLocationId(TEST_AGENCY_ID).build();
        when(nomisService.getOffender(OFFENDER_NO)).thenReturn(Collections.singletonList(summary));

        final OffenderKeyworker allocation = OffenderKeyworker.builder().prisonId(TEST_AGENCY_ID).build();
        when(offenderKeyworkerRepository.findByActiveAndOffenderNo(true, OFFENDER_NO)).thenReturn(Collections.singletonList(allocation));

        // test deallocation count
        keyworkerPool = initKeyworkerPool(keyworkerService, offenderKeyworkerRepository, nomisService, keyworkers, capacityTiers);

        Map deallocatedMap = (Map)ReflectionTestUtils.getField(keyworkerPool, "keyworkerRecentlyDeallocatedNumber");
        // Assertions - number of allocations should include active allocations only, not recently expired allocation
        //              because offender who is subject of recently expired allocation is now allocated to a different
        //              Key worker.
        assertThat(deallocatedMap.get(STAFF_ID)).isEqualTo(0);
        verify(offenderKeyworkerRepository, never()).findByActiveAndOffenderNo(true, OFFENDER_NO);
    }
}