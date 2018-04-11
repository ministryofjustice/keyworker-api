package uk.gov.justice.digital.hmpps.keyworker.services;

import org.apache.commons.lang3.StringUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.runners.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;
import org.springframework.boot.actuate.metrics.CounterService;
import org.springframework.boot.actuate.metrics.Metric;
import org.springframework.boot.actuate.metrics.buffer.BufferMetricReader;
import uk.gov.justice.digital.hmpps.keyworker.dto.KeyworkerDto;
import uk.gov.justice.digital.hmpps.keyworker.dto.OffenderLocationDto;
import uk.gov.justice.digital.hmpps.keyworker.dto.Page;
import uk.gov.justice.digital.hmpps.keyworker.dto.SortOrder;
import uk.gov.justice.digital.hmpps.keyworker.exception.AllocationException;
import uk.gov.justice.digital.hmpps.keyworker.exception.PrisonNotSupportedException;
import uk.gov.justice.digital.hmpps.keyworker.model.AllocationReason;
import uk.gov.justice.digital.hmpps.keyworker.model.AllocationType;
import uk.gov.justice.digital.hmpps.keyworker.model.OffenderKeyworker;
import uk.gov.justice.digital.hmpps.keyworker.repository.OffenderKeyworkerRepository;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.longThat;
import static org.mockito.Mockito.*;
import static uk.gov.justice.digital.hmpps.keyworker.services.KeyworkerAutoAllocationService.COUNTER_METRIC_KEYWORKER_AUTO_ALLOCATIONS;
import static uk.gov.justice.digital.hmpps.keyworker.services.KeyworkerTestHelper.*;

/**
 * Unit test for Key worker auto-allocation service.
 */
@RunWith(MockitoJUnitRunner.class)
public class KeyworkerAutoAllocationServiceTest {
    private static final String TEST_AGENCY_ID = "LEI";

    private KeyworkerAutoAllocationService keyworkerAutoAllocationService;

    @Mock
    private KeyworkerService keyworkerService;

    @Mock
    private KeyworkerPoolFactory keyworkerPoolFactory;

    @Mock
    private BufferMetricReader metricReader;

    @Mock
    private PrisonSupportedService prisonSupportedService;

    @Mock
    private OffenderKeyworkerRepository offenderKeyworkerRepository;

    private long allocCount;

    @Before
    public void setUp() {
        // Initialise a counter service
        final CounterService counterService = new CounterService() {
            @Override
            public void increment(String metricName) {
                if (StringUtils.equals(metricName, COUNTER_METRIC_KEYWORKER_AUTO_ALLOCATIONS)) {
                    allocCount++;
                }
            }

            @Override
            public void decrement(String metricName) {
                if (StringUtils.equals(metricName, COUNTER_METRIC_KEYWORKER_AUTO_ALLOCATIONS)) {
                    allocCount--;
                }
            }

            @Override
            public void reset(String metricName) {
                if (StringUtils.equals(metricName, COUNTER_METRIC_KEYWORKER_AUTO_ALLOCATIONS)) {
                    allocCount = 0;
                }
            }
        };

        doAnswer((InvocationOnMock invocation) -> new Metric(COUNTER_METRIC_KEYWORKER_AUTO_ALLOCATIONS, allocCount))
                .when(metricReader).findOne(COUNTER_METRIC_KEYWORKER_AUTO_ALLOCATIONS);

        // Construct service under test (using mock collaborators)
        Set<String> aSet = Stream.of(TEST_AGENCY_ID).collect(Collectors.toSet());
        keyworkerAutoAllocationService =
                new KeyworkerAutoAllocationService(keyworkerService, keyworkerPoolFactory, counterService, metricReader, offenderKeyworkerRepository, prisonSupportedService);
    }

    // Each unit test below is preceded by acceptance criteria in Given-When-Then form
    // KW = Key worker
    // Available refers to a staff member having the KW role at the agency and being available (i.e. not inactive or unavailable)
    // Capacity refers to spare allocation capacity (i.e. the KW has capacity for further offender allocations)
    // Allocation refers to an extant and active relationship of an offender to a Key worker
    //   (there is a distinction between an automatically created allocation and a manually created allocation)
    // For purposes of these tests, 'multiple' means at least three or more

    // When auto-allocation process is initiated for an agency that is not currently supported by this service
    // Then auto-allocation process does not perform any allocations
    // And auto-allocation process throws an AgencyNotSupported exception
    @Test
    public void testServicePerformsNoAllocationsForUnsupportedAgency() {
        doThrow(new PrisonNotSupportedException(format("Agency [%s] is not supported by this service.", TEST_AGENCY_ID))).when(prisonSupportedService).verifyPrisonSupportsAutoAllocation(eq(TEST_AGENCY_ID));

        // Invoke auto-allocate for unsupported agency (catching expected exception)
        Throwable thrown = catchThrowable(() -> keyworkerAutoAllocationService.autoAllocate(TEST_AGENCY_ID));

        // Verify collaborator interactions
        verify(keyworkerService, never()).getUnallocatedOffenders(anyString(), anyString(), any(SortOrder.class));
        verify(keyworkerService, never()).getAvailableKeyworkers(anyString());
        verify(keyworkerService, never()).allocate(any(OffenderKeyworker.class));

        assertThat(thrown).isInstanceOf(PrisonNotSupportedException.class);
    }

    // Given that all offenders at an agency are allocated to a KW
    // When auto-allocation process is initiated
    // Then auto-allocation process does not perform any allocations
    // And auto-allocation process writes an informational log entry with an appropriate message (to be defined)
    //
    // If this test fails, automatic allocation may be attempted for an offender that is already allocated.
    @Test
    public void testServicePerformsNoAllocationsWhenAllOffendersAreAllocated() {
        // No unallocated offenders
        mockUnallocatedOffenders(TEST_AGENCY_ID, Collections.emptySet());

        // Invoke auto-allocate
        keyworkerAutoAllocationService.autoAllocate(TEST_AGENCY_ID);

        // Verify collaborator interactions and log output
        verify(keyworkerService, times(1))
                .getUnallocatedOffenders(eq(TEST_AGENCY_ID), anyString(), any(SortOrder.class));

        verify(keyworkerService, never()).getAvailableKeyworkers(anyString());
        verify(keyworkerService, never()).allocate(any(OffenderKeyworker.class));
    }

    // Given there are one or more offenders at an agency that are not allocated to a KW
    // And there are no KWs available for auto-allocation
    // When auto-allocation process is initiated
    // Then auto-allocation process does not perform any allocations
    // And auto-allocation process writes an error log entry with an appropriate message (to be defined)
    // And auto-allocation process throws an exception with an appropriate error message (to be defined)
    //
    // If this test fails, offenders may be allocated to Key workers that are not available for allocation.
    @Test
    public void testServiceErrorsWhenNoKeyWorkersAvailableForAutoAllocation() {
        // Some unallocated offenders
        mockUnallocatedOffenders(TEST_AGENCY_ID, getNextOffenderNo(3));

        // No available Key workers
        mockKeyworkers(0, 0, 0, CAPACITY_TIER_1);

        // Invoke auto-allocate (catching expected exception)
        Throwable thrown = catchThrowable(() -> keyworkerAutoAllocationService.autoAllocate(TEST_AGENCY_ID));

        // Verify collaborator interactions and log output
        verify(keyworkerService, times(1))
                .getUnallocatedOffenders(eq(TEST_AGENCY_ID), anyString(), any(SortOrder.class));

        verify(keyworkerService, times(1)).getKeyworkersAvailableforAutoAllocation(TEST_AGENCY_ID);
        verify(keyworkerService, never()).allocate(any(OffenderKeyworker.class));

        verifyException(thrown, AllocationException.class, KeyworkerAutoAllocationService.OUTCOME_NO_AVAILABLE_KEY_WORKERS);
    }

    // Given there are one or more offenders at an agency that are not allocated to a KW
    // And there are KWs available for auto-allocation
    // And no unallocated offender has previously been allocated to any of the available Key workers
    // And all available KWs are fully allocated (have no capacity)
    // When auto-allocation process is initiated
    // Then auto-allocation process does not perform any allocations
    // And auto-allocation process writes an error log entry with an appropriate message (to be defined)
    // And auto-allocation process throws an exception with an appropriate error message (to be defined)
    //
    // If this test fails, Key workers may be allocated too many offenders.
    @Test
    public void testServiceErrorsWhenNoKeyWorkersWithSpareAllocationCapacity() {
        // Some unallocated offenders
        mockUnallocatedOffenders(TEST_AGENCY_ID, getNextOffenderNo(3));

        // Some available Key workers (at full capacity)
        List<KeyworkerDto> someKeyworkers = mockKeyworkers(3, FULLY_ALLOCATED, FULLY_ALLOCATED, CAPACITY_TIER_1);

        // A Key worker pool initialised with known capacity tier.
        mockKeyworkerPool(someKeyworkers);

        // No previous allocations between unallocated offenders and available Key workers
        mockPrisonerAllocationHistory(null);

        // Invoke auto-allocate (catching expected exception)
        Throwable thrown = catchThrowable(() -> keyworkerAutoAllocationService.autoAllocate(TEST_AGENCY_ID));

        // Verify collaborator interactions and log output
        verify(keyworkerService, times(1))
                .getUnallocatedOffenders(eq(TEST_AGENCY_ID), anyString(), any(SortOrder.class));

        verify(keyworkerService, times(1)).getKeyworkersAvailableforAutoAllocation(TEST_AGENCY_ID);
        verify(keyworkerPoolFactory, times(1)).getKeyworkerPool(someKeyworkers);
        verify(keyworkerService, times(1)).getAllocationHistoryForPrisoner(anyString());

        verify(keyworkerService, never()).allocate(any(OffenderKeyworker.class));
        verifyException(thrown, AllocationException.class, KeyworkerPool.OUTCOME_ALL_KEY_WORKERS_AT_CAPACITY);
    }

    // Given an offender at an agency is not allocated to a KW
    // And there are KWs available for auto-allocation
    // And offender has previously been allocated to one of the available KWs
    // And that KW has least capacity of all available KWs (i.e. it would not normally be next in line for allocation)
    // When auto-allocation process is initiated
    // Then offender is allocated to same KW they were previously allocated to
    // And allocation is designated as an auto-allocation
    //
    // If this test fails, an offender will not be allocated to a Key worker they were previously allocated to.
    @Test
    public void testOffenderAllocationToSameKeyWorkerPreviouslyAllocated() {
        final int lowAllocCount = 1;
        final int highAllocCount = FULLY_ALLOCATED - 1;
        final String allocOffenderNo = getNextOffenderNo();
        final long allocStaffId = 2;

        // An unallocated offender
        mockUnallocatedOffenders(TEST_AGENCY_ID, Collections.singleton(allocOffenderNo));

        // Some available Key workers (with known capacities)
        KeyworkerDto previousKeyworker = getKeyworker(allocStaffId, highAllocCount, CAPACITY_TIER_1);

        List<KeyworkerDto> someKeyworkers = mockKeyworkers(
                getKeyworker(1, lowAllocCount, CAPACITY_TIER_1),
                previousKeyworker,
                getKeyworker(3, lowAllocCount, CAPACITY_TIER_1));

        // A Key worker pool initialised with known capacity tier.
        mockKeyworkerPool(someKeyworkers);

        // A previous allocation between the unallocated offender and Key worker with staffId = 2
        OffenderKeyworker previousAllocation =
                getPreviousKeyworkerAutoAllocation(TEST_AGENCY_ID, allocOffenderNo, allocStaffId);

        mockPrisonerAllocationHistory(allocOffenderNo, previousAllocation);

        // Invoke auto-allocate
        keyworkerAutoAllocationService.autoAllocate(TEST_AGENCY_ID);

        // Verify collaborator interactions and log output
        verify(keyworkerService, atLeastOnce())
                .getUnallocatedOffenders(eq(TEST_AGENCY_ID), anyString(), any(SortOrder.class));

        verify(keyworkerService, times(1)).getKeyworkersAvailableforAutoAllocation(TEST_AGENCY_ID);
        verify(keyworkerPoolFactory, times(1)).getKeyworkerPool(someKeyworkers);

        verify(keyworkerService, times(1)).getAllocationHistoryForPrisoner(eq(allocOffenderNo));

        // Expecting allocation to succeed - verify request includes expected values
        ArgumentCaptor<OffenderKeyworker> kwaArg = ArgumentCaptor.forClass(OffenderKeyworker.class);

        verify(keyworkerService, times(1)).allocate(kwaArg.capture());

        verifyAutoAllocation(kwaArg.getValue(), TEST_AGENCY_ID, allocOffenderNo, allocStaffId);
    }

    // Given an offender at an agency is not allocated to a KW
    // And offender has previously been allocated to multiple KWs at agency
    // And at least two of those KWs are available for auto-allocation at same agency
    // And all previous allocations for offender have occurred on different dates
    // When auto-allocation process is initiated
    // Then offender is allocated to the KW they were most recently previously allocated to
    // And allocation is designated as an auto-allocation
    //
    // If this test fails, an offender will not be allocated to the Key worker they were most recently allocated to.
    @Test
    public void testOffenderAllocationToMostRecentKeyWorkerPreviouslyAllocated() {
        final int lowAllocCount = 1;
        final int highAllocCount = FULLY_ALLOCATED - 1;
        final String allocOffenderNo = getNextOffenderNo();
        final long allocEarlierStaffId = 2;
        final long allocLaterStaffId = 4;

        // An unallocated offender
        mockUnallocatedOffenders(TEST_AGENCY_ID, Collections.singleton(allocOffenderNo));

        // Some available Key workers (with known capacities)
        KeyworkerDto earlierKeyworker = getKeyworker(allocEarlierStaffId, lowAllocCount, CAPACITY_TIER_1);
        KeyworkerDto laterKeyworker = getKeyworker(allocLaterStaffId, highAllocCount, CAPACITY_TIER_1);

        List<KeyworkerDto> someKeyworkers = mockKeyworkers(
                getKeyworker(1, lowAllocCount + 1, CAPACITY_TIER_1),
                laterKeyworker,
                getKeyworker(3, lowAllocCount + 2, CAPACITY_TIER_1),
                earlierKeyworker);

        // A Key worker pool initialised with known capacity tier.
        mockKeyworkerPool(someKeyworkers);

        // Previous allocations between the unallocated offender and different Key workers at different date/times
        LocalDateTime assignedEarlier = LocalDateTime.now().minusMonths(9);
        LocalDateTime assignedLater = assignedEarlier.plusMonths(3);

        OffenderKeyworker prevEarlierAllocation =
                getPreviousKeyworkerAutoAllocation(TEST_AGENCY_ID, allocOffenderNo, allocEarlierStaffId, assignedEarlier);

        OffenderKeyworker prevLaterAllocation =
                getPreviousKeyworkerAutoAllocation(TEST_AGENCY_ID, allocOffenderNo, allocLaterStaffId, assignedLater);

        mockPrisonerAllocationHistory(allocOffenderNo, prevEarlierAllocation, prevLaterAllocation);

        // Invoke auto-allocate
        keyworkerAutoAllocationService.autoAllocate(TEST_AGENCY_ID);

        // Verify collaborator interactions and log output
        verify(keyworkerService, atLeastOnce())
                .getUnallocatedOffenders(eq(TEST_AGENCY_ID), anyString(), any(SortOrder.class));

        verify(keyworkerService, times(1)).getKeyworkersAvailableforAutoAllocation(TEST_AGENCY_ID);
        verify(keyworkerPoolFactory, times(1)).getKeyworkerPool(someKeyworkers);

        verify(keyworkerService, times(1)).getAllocationHistoryForPrisoner(eq(allocOffenderNo));

        // Expecting allocation to succeed - verify request includes expected values
        ArgumentCaptor<OffenderKeyworker> kwaArg = ArgumentCaptor.forClass(OffenderKeyworker.class);

        verify(keyworkerService, times(1)).allocate(kwaArg.capture());

        verifyAutoAllocation(kwaArg.getValue(), TEST_AGENCY_ID, allocOffenderNo, allocLaterStaffId);
    }

    // Given an offender at an agency is not allocated to a KW
    // And offender has had no previous KW allocation at the agency
    // And multiple, available KWs have capacity (current number of allocations less than Tier-1 capacity level)
    // And one KW has more capacity than any other KW
    // When auto-allocation process is initiated
    // Then offender is allocated to the KW with most capacity
    // And allocation is designated as an auto-allocation
    //
    // If this test fails, an offender will not be auto-allocated to the offender with most capacity.
    @Test
    public void testOffenderAllocationToKeyWorkerWithinTier1CapacityWithLeastAllocations() {
        final int lowAllocCount = 1;
        final int highAllocCount = FULLY_ALLOCATED - 1;
        final long leastAllocStaffId = 3;
        final String allocOffenderNo = getNextOffenderNo();

        // An unallocated offender
        mockUnallocatedOffenders(TEST_AGENCY_ID, Collections.singleton(allocOffenderNo));

        // Some available Key workers (with known capacities)
        KeyworkerDto leastAllocKeyworker = getKeyworker(leastAllocStaffId, lowAllocCount, CAPACITY_TIER_1);

        List<KeyworkerDto> someKeyworkers = mockKeyworkers(
                getKeyworker(1, highAllocCount, CAPACITY_TIER_1),
                getKeyworker(2, highAllocCount, CAPACITY_TIER_1),
                leastAllocKeyworker);

        // A Key worker pool initialised with known capacity tier.
        mockKeyworkerPool(someKeyworkers);

        // No previous allocations between unallocated offender and available Key workers

        // Invoke auto-allocate
        keyworkerAutoAllocationService.autoAllocate(TEST_AGENCY_ID);

        // Verify collaborator interactions and log output
        verify(keyworkerService, atLeastOnce())
                .getUnallocatedOffenders(eq(TEST_AGENCY_ID), anyString(), any(SortOrder.class));

        verify(keyworkerService, times(1)).getKeyworkersAvailableforAutoAllocation(TEST_AGENCY_ID);

        verify(keyworkerPoolFactory, times(1)).getKeyworkerPool(someKeyworkers);
        verify(keyworkerService, times(1)).getAllocationHistoryForPrisoner(anyString());
        // Expecting allocation to succeed - verify request includes expected values
        ArgumentCaptor<OffenderKeyworker> kwaArg = ArgumentCaptor.forClass(OffenderKeyworker.class);

        verify(keyworkerService, times(1)).allocate(kwaArg.capture());

        verifyAutoAllocation(kwaArg.getValue(), TEST_AGENCY_ID, allocOffenderNo, leastAllocStaffId);
    }

    // Given an offender at an agency is not allocated to a KW
    // And offender has had no previous KW allocation at the agency
    // And multiple, available KWs have capacity (current number of allocations less than Tier-1 capacity level)
    // And all KWs have at least one auto-allocated offender
    // And all allocations have occurred on different dates
    // And at least two KWs have same capacity and more capacity than any other KW at agency
    // When auto-allocation process is initiated
    // Then offender is allocated to the KW with most capacity and the least recent auto-allocation
    // And allocation is designated as an auto-allocation
    //
    // If this test fails, an offender will not be auto-allocated to the offender with most capacity and least recent
    // auto-allocation.
    @Test
    public void testOffenderAllocationToKeyWorkerWithinTier1CapacityWithLeastAllocationsAndLeastRecentAllocation() {
        final int lowAllocCount = 1;
        final int highAllocCount = FULLY_ALLOCATED - 1;
        final String allocOffenderNo = getNextOffenderNo();
        final long recentLeastAllocStaffId = 3;
        final long olderLeastAllocStaffId = 4;

        // An unallocated offender
        mockUnallocatedOffenders(TEST_AGENCY_ID, Collections.singleton(allocOffenderNo));

        // Some available Key workers (with known capacities)
        KeyworkerDto recentLeastAllocKeyworker = getKeyworker(recentLeastAllocStaffId, lowAllocCount, CAPACITY_TIER_1);
        KeyworkerDto olderLeastAllocKeyworker = getKeyworker(olderLeastAllocStaffId, lowAllocCount, CAPACITY_TIER_1);

        List<KeyworkerDto> someKeyworkers = mockKeyworkers(
                getKeyworker(1, highAllocCount, CAPACITY_TIER_1),
                getKeyworker(2, highAllocCount, CAPACITY_TIER_1),
                recentLeastAllocKeyworker,
                olderLeastAllocKeyworker);

        // A Key worker pool initialised with known capacity tier.
        mockKeyworkerPool(someKeyworkers);

        // No previous allocations between unallocated offender and available Key workers
        mockPrisonerAllocationHistory(allocOffenderNo);

        // Some previous auto-allocations for Key workers of interest
        LocalDateTime refDateTime = LocalDateTime.now();

        OffenderKeyworker recentAllocation = getPreviousKeyworkerAutoAllocation(
                TEST_AGENCY_ID, "A5555AA", recentLeastAllocStaffId, refDateTime.minusDays(2));

        OffenderKeyworker olderAllocation = getPreviousKeyworkerAutoAllocation(
                TEST_AGENCY_ID, "A7777AA", olderLeastAllocStaffId, refDateTime.minusDays(7));

        mockKeyworkerAllocationHistory(recentLeastAllocStaffId, recentAllocation);
        mockKeyworkerAllocationHistory(olderLeastAllocStaffId, olderAllocation);

        // Invoke auto-allocate
        keyworkerAutoAllocationService.autoAllocate(TEST_AGENCY_ID);

        // Verify collaborator interactions and log output
        verify(keyworkerService, atLeastOnce())
                .getUnallocatedOffenders(eq(TEST_AGENCY_ID), anyString(), any(SortOrder.class));

        verify(keyworkerService, times(1)).getKeyworkersAvailableforAutoAllocation(TEST_AGENCY_ID);
        verify(keyworkerPoolFactory, times(1)).getKeyworkerPool(someKeyworkers);
        verify(keyworkerService, times(1)).getAllocationHistoryForPrisoner(eq(allocOffenderNo));
        verify(keyworkerService, times(2)).getAllocationsForKeyworker(anyLong());

        // Expecting allocation to succeed - verify request includes expected values
        ArgumentCaptor<OffenderKeyworker> kwaArg = ArgumentCaptor.forClass(OffenderKeyworker.class);

        verify(keyworkerService, times(1)).allocate(kwaArg.capture());

        KeyworkerTestHelper.verifyAutoAllocation(kwaArg.getValue(), TEST_AGENCY_ID, allocOffenderNo, olderLeastAllocStaffId);
    }

    // Given multiple pages (page size = 10) of offenders at an agency are not allocated to a KW
    // And offenders have no previous KW allocation at the agency
    // And multiple, available KWs have capacity
    // And total capacity, across all KWs, is sufficient to allow all offenders to be allocated
    // When auto-allocation process is initiated
    // Then all offenders are allocated to a KW
    // And all allocations are designated as auto-allocations
    //
    // If this test fails, auto-allocation of multiple offenders may not complete successfully.
    @Test
    public void testAllOffendersAllocated() {
        Integer totalOffenders = 25;
        Integer totalKeyworkers = 5;

        mockUnallocatedOffenders(TEST_AGENCY_ID, getNextOffenderNo(totalOffenders));

        // Enough available Key workers (with enough total capacity to allocate all offenders)
        List<KeyworkerDto> someKeyworkers = mockKeyworkers(totalKeyworkers, 0, 0, CAPACITY_TIER_1);

        // A Key worker pool initialised with known capacity tier.
        mockKeyworkerPool(someKeyworkers);

        // No previous allocations between any offender and available Key workers
        mockPrisonerAllocationHistory(null);

        // Invoke auto-allocate
        keyworkerAutoAllocationService.autoAllocate(TEST_AGENCY_ID);

        // Verify collaborator interactions and log output
        verify(keyworkerService, times(1))
                .getUnallocatedOffenders(eq(TEST_AGENCY_ID), anyString(), any(SortOrder.class));

        verify(keyworkerService, times(1)).getKeyworkersAvailableforAutoAllocation(TEST_AGENCY_ID);
        verify(keyworkerPoolFactory, times(1)).getKeyworkerPool(someKeyworkers);

        verify(keyworkerService, times(totalOffenders)).getAllocationHistoryForPrisoner(anyString());

        // Expecting allocation to succeed - verify request includes expected values
        ArgumentCaptor<OffenderKeyworker> kwaArg = ArgumentCaptor.forClass(OffenderKeyworker.class);

        verify(keyworkerService, times(totalOffenders)).allocate(kwaArg.capture());

        kwaArg.getAllValues().forEach(kwAlloc -> {
            assertThat(kwAlloc.getOffenderNo()).isNotNull();
            assertThat(kwAlloc.getStaffId()).isBetween(1L, totalKeyworkers.longValue());
            assertThat(kwAlloc.getAllocationType()).isEqualTo(AllocationType.PROVISIONAL);
            assertThat(kwAlloc.getAllocationReason()).isEqualTo(AllocationReason.AUTO);
        });
    }

    // Given multiple pages (page size = 10) of offenders at an agency are not allocated to a KW
    // And offenders have no previous KW allocation at the agency
    // And multiple, available KWs have capacity
    // And total capacity, across all KWs, is not sufficient to allow all offenders to be allocated
    // When auto-allocation process is initiated
    // Then offenders are allocated to a KW whilst there is capacity
    // And all successful allocations are designated as auto-allocations
    // And auto-allocation process writes an error log entry with an appropriate message (to be defined)
    // And auto-allocation process throws an exception with an appropriate error message (to be defined)
    //
    // If this test fails, auto-allocation may fail to allocate some offenders when there is capacity.
    @Test
    public void testSomeOffendersAllocatedBeforeErrorDueToNoCapacity() {
        Integer totalOffenders = 25;
        Integer totalKeyworkers = 5;

        mockUnallocatedOffenders(TEST_AGENCY_ID, getNextOffenderNo(totalOffenders));

        // Some available Key workers with some capacity but not enough total capacity to allocate all offenders
        List<KeyworkerDto> someKeyworkers = mockKeyworkers(totalKeyworkers,FULLY_ALLOCATED - 2, FULLY_ALLOCATED, CAPACITY_TIER_2);

        // Determine available capacity
        int totalCapacity = (totalKeyworkers * FULLY_ALLOCATED) -
                someKeyworkers.stream().mapToInt(KeyworkerDto::getNumberAllocated).sum();

        // A Key worker pool initialised with known capacity tier.
        mockKeyworkerPool(someKeyworkers);

        // No previous allocations between any offender and available Key workers
        mockPrisonerAllocationHistory(null);

        // Invoke auto-allocate (catching expected exception)
        Throwable thrown = catchThrowable(() -> keyworkerAutoAllocationService.autoAllocate(TEST_AGENCY_ID));

        // Verify collaborator interactions and log output
        verify(keyworkerService, times(1))
                .getUnallocatedOffenders(eq(TEST_AGENCY_ID), anyString(), any(SortOrder.class));

        verify(keyworkerService, times(1)).getKeyworkersAvailableforAutoAllocation(TEST_AGENCY_ID);
        verify(keyworkerPoolFactory, times(1)).getKeyworkerPool(someKeyworkers);

        verify(keyworkerService, atLeast(totalCapacity)).getAllocationHistoryForPrisoner(anyString());

        // Expecting allocation to succeed - verify request includes expected values
        ArgumentCaptor<OffenderKeyworker> kwaArg = ArgumentCaptor.forClass(OffenderKeyworker.class);

        verify(keyworkerService, times(totalCapacity)).allocate(kwaArg.capture());

        kwaArg.getAllValues().forEach(kwAlloc -> {
            assertThat(kwAlloc.getOffenderNo()).isNotNull();
            assertThat(kwAlloc.getStaffId()).isBetween(1L, totalKeyworkers.longValue());
            assertThat(kwAlloc.getAllocationType()).isEqualTo(AllocationType.PROVISIONAL);
            assertThat(kwAlloc.getAllocationReason()).isEqualTo(AllocationReason.AUTO);
        });

        verifyException(thrown, AllocationException.class, KeyworkerPool.OUTCOME_ALL_KEY_WORKERS_AT_CAPACITY);
    }

    private void mockUnallocatedOffenders(String prisonId, Set<String> offenderNos) {
        final String[] offNos = offenderNos.toArray(new String[0]);

        List<OffenderLocationDto> unallocatedOffenders = new ArrayList<>();

        for (int i = 0; i < offNos.length; i++) {
            unallocatedOffenders.add(KeyworkerTestHelper.getOffender(i + 1, prisonId, offNos[i], true));
        }

        when(keyworkerService.getUnallocatedOffenders(eq(prisonId), anyString(), any(SortOrder.class))).thenReturn(unallocatedOffenders);
    }

    // Provides page of unallocated offenders (consistent with supplied pagination parameters)
    private Page<OffenderLocationDto> pagedUnallocatedOffenders(String prisonId, Set<String> offenderNos, long total, long startId, long limit) {
        final String[] offNos = offenderNos.toArray(new String[0]);

        List<OffenderLocationDto> unallocatedOffenders = new ArrayList<>();

        for (long i = 0; i < Math.min(total, limit); i++) {
            int idx = Long.valueOf(startId + i).intValue() - 1;

            unallocatedOffenders.add(KeyworkerTestHelper.getOffender(startId + i, prisonId, offNos[idx], true));
        }

        return new Page<>(unallocatedOffenders, total, 0L, limit);
    }

    private List<KeyworkerDto> mockKeyworkers(long total, int minAllocations, int maxAllocations, int capacity) {
        List<KeyworkerDto> mockKeyworkers;

        if (total > 0) {
            mockKeyworkers = KeyworkerTestHelper.getKeyworkers(total, minAllocations, maxAllocations, capacity);
        } else {
            mockKeyworkers = Collections.emptyList();
        }

        when(keyworkerService.getKeyworkersAvailableforAutoAllocation(anyString())).thenReturn(mockKeyworkers);

        return mockKeyworkers;
    }

    private List<KeyworkerDto> mockKeyworkers(KeyworkerDto... keyworkers) {
        List<KeyworkerDto> mockKeyworkers;

        if (keyworkers.length > 0) {
            mockKeyworkers = Arrays.asList(keyworkers);
        } else {
            mockKeyworkers = Collections.emptyList();
        }

        when(keyworkerService.getKeyworkersAvailableforAutoAllocation(anyString())).thenReturn(mockKeyworkers);

        return mockKeyworkers;
    }

    private void mockKeyworkerPool(List<KeyworkerDto> keyworkers) {
        KeyworkerPool keyworkerPool = KeyworkerTestHelper.initKeyworkerPool(keyworkerService, offenderKeyworkerRepository, null,
                keyworkers, Collections.singletonList(FULLY_ALLOCATED));

        when(keyworkerPoolFactory.getKeyworkerPool(keyworkers)).thenReturn(keyworkerPool);
    }

    private void mockPrisonerAllocationHistory(String offenderNo, OffenderKeyworker... allocations) {
        List<OffenderKeyworker> allocationHistory =
                (allocations == null) ? Collections.emptyList() : Arrays.asList(allocations);

        if (StringUtils.isBlank(offenderNo)) {
            when(keyworkerService.getAllocationHistoryForPrisoner(anyString())).thenReturn(allocationHistory);
        } else {
            when(keyworkerService.getAllocationHistoryForPrisoner(eq(offenderNo))).thenReturn(allocationHistory);
        }
    }

    private void mockKeyworkerAllocationHistory(Long staffId, OffenderKeyworker... allocations) {
        List<OffenderKeyworker> allocationHistory =
                (allocations == null) ? Collections.emptyList() : Arrays.asList(allocations);

        when(keyworkerService.getAllocationsForKeyworker(eq(staffId))).thenReturn(allocationHistory);
    }

    class IsLongBetween extends ArgumentMatcher<Long> {
        private final long lowerBound;
        private final long upperBound;

        IsLongBetween(long lowerBound, long upperBound) {
            this.lowerBound = lowerBound;
            this.upperBound = upperBound;
        }

        @Override
        public boolean matches(Object argument) {
            long argVal = (Long) argument;

            return (argVal >= lowerBound) && (argVal <= upperBound);
        }
    }

    private long isLongBetween(long lowerBound, long upperBound) {
        return longThat(new IsLongBetween(lowerBound, upperBound));
    }
}
