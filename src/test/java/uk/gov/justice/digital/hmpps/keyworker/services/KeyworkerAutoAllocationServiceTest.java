package uk.gov.justice.digital.hmpps.keyworker.services;

import io.micrometer.core.instrument.MeterRegistry;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.digital.hmpps.keyworker.dto.KeyworkerDto;
import uk.gov.justice.digital.hmpps.keyworker.dto.OffenderLocationDto;
import uk.gov.justice.digital.hmpps.keyworker.dto.Page;
import uk.gov.justice.digital.hmpps.keyworker.dto.Prison;
import uk.gov.justice.digital.hmpps.keyworker.exception.AllocationException;
import uk.gov.justice.digital.hmpps.keyworker.exception.PrisonNotSupportedException;
import uk.gov.justice.digital.hmpps.keyworker.model.AllocationReason;
import uk.gov.justice.digital.hmpps.keyworker.model.AllocationType;
import uk.gov.justice.digital.hmpps.keyworker.model.OffenderKeyworker;
import uk.gov.justice.digital.hmpps.keyworker.repository.OffenderKeyworkerRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.justice.digital.hmpps.keyworker.services.KeyworkerTestHelper.CAPACITY_TIER_1;
import static uk.gov.justice.digital.hmpps.keyworker.services.KeyworkerTestHelper.CAPACITY_TIER_2;
import static uk.gov.justice.digital.hmpps.keyworker.services.KeyworkerTestHelper.FULLY_ALLOCATED;
import static uk.gov.justice.digital.hmpps.keyworker.services.KeyworkerTestHelper.getKeyworker;
import static uk.gov.justice.digital.hmpps.keyworker.services.KeyworkerTestHelper.getNextOffenderNo;
import static uk.gov.justice.digital.hmpps.keyworker.services.KeyworkerTestHelper.getPreviousKeyworkerAutoAllocation;
import static uk.gov.justice.digital.hmpps.keyworker.services.KeyworkerTestHelper.verifyAutoAllocation;
import static uk.gov.justice.digital.hmpps.keyworker.services.KeyworkerTestHelper.verifyException;

/**
 * Unit test for Key worker auto-allocation service.
 */
@ExtendWith(MockitoExtension.class)
class KeyworkerAutoAllocationServiceTest {
    private static final String TEST_AGENCY_ID = "LEI";
    private static final String TEST_COMPLEX_OFFENDERS = "G6415GD,G8930UW";

    private KeyworkerAutoAllocationService keyworkerAutoAllocationService;

    @Mock
    private KeyworkerService keyworkerService;

    @Mock
    private KeyworkerPoolFactory keyworkerPoolFactory;

    @Mock
    private MeterRegistry metricReader;

    @Mock
    private PrisonSupportedService prisonSupportedService;

    @Mock
    private OffenderKeyworkerRepository offenderKeyworkerRepository;

    private MoicService moicService;

    private long allocCount;

    @BeforeEach
    void setUp() {

        moicService = new MoicService(TEST_COMPLEX_OFFENDERS, TEST_AGENCY_ID);

        // Construct service under test (using mock collaborators)
        final var aSet = Stream.of(TEST_AGENCY_ID).collect(Collectors.toSet());

        final var prisonDetail = Prison.builder()
            .prisonId(TEST_AGENCY_ID).capacityTier1(CAPACITY_TIER_1).capacityTier2(CAPACITY_TIER_2)
            .build();
        lenient().when(prisonSupportedService.getPrisonDetail(TEST_AGENCY_ID)).thenReturn(prisonDetail);

        keyworkerAutoAllocationService =
            new KeyworkerAutoAllocationService(keyworkerService, keyworkerPoolFactory, offenderKeyworkerRepository, prisonSupportedService, moicService);
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
    void testServicePerformsNoAllocationsForUnsupportedAgency() {
        doThrow(new PrisonNotSupportedException(format("Agency [%s] is not supported by this service.", TEST_AGENCY_ID))).when(prisonSupportedService).verifyPrisonSupportsAutoAllocation(eq(TEST_AGENCY_ID));

        // Invoke auto-allocate for unsupported agency (catching expected exception)
        final var thrown = catchThrowable(() -> keyworkerAutoAllocationService.autoAllocate(TEST_AGENCY_ID));

        // Verify collaborator interactions
        verify(keyworkerService, never()).getUnallocatedOffenders(anyString(), isNull(), isNull());
        verify(keyworkerService, never()).getAvailableKeyworkers(anyString(), eq(false));
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
    void testServicePerformsNoAllocationsWhenAllOffendersAreAllocated() {
        // No unallocated offenders
        mockUnallocatedOffenders(TEST_AGENCY_ID, Collections.emptySet());

        // Invoke auto-allocate
        keyworkerAutoAllocationService.autoAllocate(TEST_AGENCY_ID);

        // Verify collaborator interactions and log output
        verify(keyworkerService, times(1))
            .getUnallocatedOffenders(eq(TEST_AGENCY_ID), isNull(), isNull());

        verify(keyworkerService, never()).getAvailableKeyworkers(anyString(), eq(false));
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
    void testServiceErrorsWhenNoKeyWorkersAvailableForAutoAllocation() {
        // Some unallocated offenders
        mockUnallocatedOffenders(TEST_AGENCY_ID, getNextOffenderNo(3));

        // No available Key workers
        mockKeyworkers(0, 0, 0, CAPACITY_TIER_1);

        // Invoke auto-allocate (catching expected exception)
        final var thrown = catchThrowable(() -> keyworkerAutoAllocationService.autoAllocate(TEST_AGENCY_ID));

        // Verify collaborator interactions and log output
        verify(keyworkerService, times(1))
            .getUnallocatedOffenders(eq(TEST_AGENCY_ID), isNull(), isNull());

        verify(keyworkerService, times(1)).getKeyworkersAvailableForAutoAllocation(TEST_AGENCY_ID);
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
    void testServiceErrorsWhenNoKeyWorkersWithSpareAllocationCapacity() {
        // Some unallocated offenders
        mockUnallocatedOffenders(TEST_AGENCY_ID, getNextOffenderNo(3));

        // Some available Key workers (at full capacity)
        final var someKeyworkers = mockKeyworkers(3, FULLY_ALLOCATED, FULLY_ALLOCATED, CAPACITY_TIER_1);

        // A Key worker pool initialised with known capacity tier.
        mockKeyworkerPool(someKeyworkers);

        // No previous allocations between unallocated offenders and available Key workers
        mockPrisonerAllocationHistory(null);

        // Invoke auto-allocate (catching expected exception)
        final var thrown = catchThrowable(() -> keyworkerAutoAllocationService.autoAllocate(TEST_AGENCY_ID));

        // Verify collaborator interactions and log output
        verify(keyworkerService, times(1))
            .getUnallocatedOffenders(eq(TEST_AGENCY_ID), isNull(), isNull());

        verify(keyworkerService, times(1)).getKeyworkersAvailableForAutoAllocation(TEST_AGENCY_ID);
        verify(keyworkerPoolFactory, times(1)).getKeyworkerPool(TEST_AGENCY_ID, someKeyworkers);
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
    void testOffenderAllocationToSameKeyWorkerPreviouslyAllocated() {
        final var lowAllocCount = 1;
        final var highAllocCount = FULLY_ALLOCATED - 1;
        final var allocOffenderNo = getNextOffenderNo();
        final long allocStaffId = 2;

        // An unallocated offender
        mockUnallocatedOffenders(TEST_AGENCY_ID, Collections.singleton(allocOffenderNo));

        // Some available Key workers (with known capacities)
        final var previousKeyworker = getKeyworker(allocStaffId, highAllocCount, CAPACITY_TIER_1);

        final var someKeyworkers = mockKeyworkers(
            getKeyworker(1, lowAllocCount, CAPACITY_TIER_1),
            previousKeyworker,
            getKeyworker(3, lowAllocCount, CAPACITY_TIER_1));

        // A Key worker pool initialised with known capacity tier.
        mockKeyworkerPool(someKeyworkers);

        // A previous allocation between the unallocated offender and Key worker with staffId = 2
        final var previousAllocation =
            getPreviousKeyworkerAutoAllocation(TEST_AGENCY_ID, allocOffenderNo, allocStaffId);

        mockPrisonerAllocationHistory(allocOffenderNo, previousAllocation);

        // Invoke auto-allocate
        keyworkerAutoAllocationService.autoAllocate(TEST_AGENCY_ID);

        // Verify collaborator interactions and log output
        verify(keyworkerService, atLeastOnce())
            .getUnallocatedOffenders(eq(TEST_AGENCY_ID), isNull(), isNull());

        verify(keyworkerService, times(1)).getKeyworkersAvailableForAutoAllocation(TEST_AGENCY_ID);
        verify(keyworkerPoolFactory, times(1)).getKeyworkerPool(TEST_AGENCY_ID, someKeyworkers);

        verify(keyworkerService, times(1)).getAllocationHistoryForPrisoner(eq(allocOffenderNo));

        // Expecting allocation to succeed - verify request includes expected values
        final var kwaArg = ArgumentCaptor.forClass(OffenderKeyworker.class);

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
    void testOffenderAllocationToMostRecentKeyWorkerPreviouslyAllocated() {
        final var lowAllocCount = 1;
        final var highAllocCount = FULLY_ALLOCATED - 1;
        final var allocOffenderNo = getNextOffenderNo();
        final long allocEarlierStaffId = 2;
        final long allocLaterStaffId = 4;

        // An unallocated offender
        mockUnallocatedOffenders(TEST_AGENCY_ID, Collections.singleton(allocOffenderNo));

        // Some available Key workers (with known capacities)
        final var earlierKeyworker = getKeyworker(allocEarlierStaffId, lowAllocCount, CAPACITY_TIER_1);
        final var laterKeyworker = getKeyworker(allocLaterStaffId, highAllocCount, CAPACITY_TIER_1);

        final var someKeyworkers = mockKeyworkers(
            getKeyworker(1, lowAllocCount + 1, CAPACITY_TIER_1),
            laterKeyworker,
            getKeyworker(3, lowAllocCount + 2, CAPACITY_TIER_1),
            earlierKeyworker);

        // A Key worker pool initialised with known capacity tier.
        mockKeyworkerPool(someKeyworkers);

        // Previous allocations between the unallocated offender and different Key workers at different date/times
        final var assignedEarlier = LocalDateTime.now().minusMonths(9);
        final var assignedLater = assignedEarlier.plusMonths(3);

        final var prevEarlierAllocation =
            getPreviousKeyworkerAutoAllocation(TEST_AGENCY_ID, allocOffenderNo, allocEarlierStaffId, assignedEarlier);

        final var prevLaterAllocation =
            getPreviousKeyworkerAutoAllocation(TEST_AGENCY_ID, allocOffenderNo, allocLaterStaffId, assignedLater);

        mockPrisonerAllocationHistory(allocOffenderNo, prevEarlierAllocation, prevLaterAllocation);

        // Invoke auto-allocate
        keyworkerAutoAllocationService.autoAllocate(TEST_AGENCY_ID);

        // Verify collaborator interactions and log output
        verify(keyworkerService, atLeastOnce())
            .getUnallocatedOffenders(eq(TEST_AGENCY_ID), isNull(), isNull());

        verify(keyworkerService, times(1)).getKeyworkersAvailableForAutoAllocation(TEST_AGENCY_ID);
        verify(keyworkerPoolFactory, times(1)).getKeyworkerPool(TEST_AGENCY_ID, someKeyworkers);

        verify(keyworkerService, times(1)).getAllocationHistoryForPrisoner(eq(allocOffenderNo));

        // Expecting allocation to succeed - verify request includes expected values
        final var kwaArg = ArgumentCaptor.forClass(OffenderKeyworker.class);

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
    void testOffenderAllocationToKeyWorkerWithinTier1CapacityWithLeastAllocations() {
        final var lowAllocCount = 1;
        final var highAllocCount = FULLY_ALLOCATED - 1;
        final long leastAllocStaffId = 3;
        final var allocOffenderNo = getNextOffenderNo();

        // An unallocated offender
        mockUnallocatedOffenders(TEST_AGENCY_ID, Collections.singleton(allocOffenderNo));

        // Some available Key workers (with known capacities)
        final var leastAllocKeyworker = getKeyworker(leastAllocStaffId, lowAllocCount, CAPACITY_TIER_1);

        final var someKeyworkers = mockKeyworkers(
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
            .getUnallocatedOffenders(eq(TEST_AGENCY_ID), isNull(), isNull());

        verify(keyworkerService, times(1)).getKeyworkersAvailableForAutoAllocation(TEST_AGENCY_ID);

        verify(keyworkerPoolFactory, times(1)).getKeyworkerPool(TEST_AGENCY_ID, someKeyworkers);
        verify(keyworkerService, times(1)).getAllocationHistoryForPrisoner(anyString());
        // Expecting allocation to succeed - verify request includes expected values
        final var kwaArg = ArgumentCaptor.forClass(OffenderKeyworker.class);

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
    void testOffenderAllocationToKeyWorkerWithinTier1CapacityWithLeastAllocationsAndLeastRecentAllocation() {
        final var lowAllocCount = 1;
        final var highAllocCount = FULLY_ALLOCATED - 1;
        final var allocOffenderNo = getNextOffenderNo();
        final long recentLeastAllocStaffId = 3;
        final long olderLeastAllocStaffId = 4;

        // An unallocated offender
        mockUnallocatedOffenders(TEST_AGENCY_ID, Collections.singleton(allocOffenderNo));

        // Some available Key workers (with known capacities)
        final var recentLeastAllocKeyworker = getKeyworker(recentLeastAllocStaffId, lowAllocCount, CAPACITY_TIER_1);
        final var olderLeastAllocKeyworker = getKeyworker(olderLeastAllocStaffId, lowAllocCount, CAPACITY_TIER_1);

        final var someKeyworkers = mockKeyworkers(
            getKeyworker(1, highAllocCount, CAPACITY_TIER_1),
            getKeyworker(2, highAllocCount, CAPACITY_TIER_1),
            recentLeastAllocKeyworker,
            olderLeastAllocKeyworker);

        // A Key worker pool initialised with known capacity tier.
        mockKeyworkerPool(someKeyworkers);

        // No previous allocations between unallocated offender and available Key workers
        mockPrisonerAllocationHistory(allocOffenderNo);

        // Some previous auto-allocations for Key workers of interest
        final var refDateTime = LocalDateTime.now();

        final var recentAllocation = getPreviousKeyworkerAutoAllocation(
            TEST_AGENCY_ID, "A5555AA", recentLeastAllocStaffId, refDateTime.minusDays(2));

        final var olderAllocation = getPreviousKeyworkerAutoAllocation(
            TEST_AGENCY_ID, "A7777AA", olderLeastAllocStaffId, refDateTime.minusDays(7));

        mockKeyworkerAllocationHistory(recentLeastAllocStaffId, recentAllocation);
        mockKeyworkerAllocationHistory(olderLeastAllocStaffId, olderAllocation);

        // Invoke auto-allocate
        keyworkerAutoAllocationService.autoAllocate(TEST_AGENCY_ID);

        // Verify collaborator interactions and log output
        verify(keyworkerService, atLeastOnce())
            .getUnallocatedOffenders(eq(TEST_AGENCY_ID), isNull(), isNull());

        verify(keyworkerService, times(1)).getKeyworkersAvailableForAutoAllocation(TEST_AGENCY_ID);
        verify(keyworkerPoolFactory, times(1)).getKeyworkerPool(TEST_AGENCY_ID, someKeyworkers);
        verify(keyworkerService, times(1)).getAllocationHistoryForPrisoner(eq(allocOffenderNo));
        verify(keyworkerService, times(2)).getAllocationsForKeyworker(anyLong());

        // Expecting allocation to succeed - verify request includes expected values
        final var kwaArg = ArgumentCaptor.forClass(OffenderKeyworker.class);

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
    void testAllOffendersAllocated() {
        final var totalOffenders = 25;
        final var totalKeyworkers = 5L;

        mockUnallocatedOffenders(TEST_AGENCY_ID, getNextOffenderNo(totalOffenders));

        // Enough available Key workers (with enough total capacity to allocate all offenders)
        final var someKeyworkers = mockKeyworkers(totalKeyworkers, 0, 0, CAPACITY_TIER_1);

        // A Key worker pool initialised with known capacity tier.
        mockKeyworkerPool(someKeyworkers);

        // No previous allocations between any offender and available Key workers
        mockPrisonerAllocationHistory(null);

        // Invoke auto-allocate
        final var allocated = keyworkerAutoAllocationService.autoAllocate(TEST_AGENCY_ID);

        assertThat(allocated).isEqualTo(25);

        // Verify collaborator interactions and log output
        verify(keyworkerService, times(1))
            .getUnallocatedOffenders(eq(TEST_AGENCY_ID), isNull(), isNull());

        verify(keyworkerService, times(1)).getKeyworkersAvailableForAutoAllocation(TEST_AGENCY_ID);
        verify(keyworkerPoolFactory, times(1)).getKeyworkerPool(TEST_AGENCY_ID, someKeyworkers);

        verify(keyworkerService, times(totalOffenders)).getAllocationHistoryForPrisoner(anyString());

        // Expecting allocation to succeed - verify request includes expected values
        final var kwaArg = ArgumentCaptor.forClass(OffenderKeyworker.class);

        verify(keyworkerService, times(totalOffenders)).allocate(kwaArg.capture());

        kwaArg.getAllValues().forEach(kwAlloc -> {
            assertThat(kwAlloc.getOffenderNo()).isNotNull();
            assertThat(kwAlloc.getStaffId()).isBetween(1L, totalKeyworkers);
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
    void testSomeOffendersAllocatedBeforeErrorDueToNoCapacity() {
        final var totalOffenders = 25;
        final var totalKeyworkers = 5;

        mockUnallocatedOffenders(TEST_AGENCY_ID, getNextOffenderNo(totalOffenders));

        // Some available Key workers with some capacity but not enough total capacity to allocate all offenders
        final var someKeyworkers = mockKeyworkers(totalKeyworkers, FULLY_ALLOCATED - 2, FULLY_ALLOCATED, CAPACITY_TIER_1);

        // Determine available capacity
        final var totalCapacity = (totalKeyworkers * FULLY_ALLOCATED) -
            someKeyworkers.stream().mapToInt(KeyworkerDto::getNumberAllocated).sum();

        // A Key worker pool initialised with known capacity tier.
        mockKeyworkerPool(someKeyworkers);

        // No previous allocations between any offender and available Key workers
        mockPrisonerAllocationHistory(null);

        // Invoke auto-allocate (catching expected exception)
        final var thrown = catchThrowable(() -> keyworkerAutoAllocationService.autoAllocate(TEST_AGENCY_ID));

        verifyException(thrown, AllocationException.class, KeyworkerPool.OUTCOME_ALL_KEY_WORKERS_AT_CAPACITY);

        // Verify collaborator interactions and log output
        verify(keyworkerService, times(1))
            .getUnallocatedOffenders(eq(TEST_AGENCY_ID), isNull(), isNull());

        verify(keyworkerService, times(1)).getKeyworkersAvailableForAutoAllocation(TEST_AGENCY_ID);
        verify(keyworkerPoolFactory, times(1)).getKeyworkerPool(TEST_AGENCY_ID, someKeyworkers);

        verify(keyworkerService, atLeast(totalCapacity)).getAllocationHistoryForPrisoner(anyString());

        // Expecting allocation to succeed - verify request includes expected values
        final var kwaArg = ArgumentCaptor.forClass(OffenderKeyworker.class);

        verify(keyworkerService, times(totalCapacity)).allocate(kwaArg.capture());

        kwaArg.getAllValues().forEach(kwAlloc -> {
            assertThat(kwAlloc.getOffenderNo()).isNotNull();
            assertThat(kwAlloc.getStaffId()).isBetween(1L, (long) totalKeyworkers);
            assertThat(kwAlloc.getAllocationType()).isEqualTo(AllocationType.PROVISIONAL);
            assertThat(kwAlloc.getAllocationReason()).isEqualTo(AllocationReason.AUTO);
        });
    }

    @Test
    void testComplexOffendersAreSkipped() {
        mockUnallocatedOffenders(TEST_AGENCY_ID, Set.of("A12345", "G6415GD", "G8930UW"));
        mockKeyworkerPool(mockKeyworkers(1, 0, FULLY_ALLOCATED, CAPACITY_TIER_2));
        mockPrisonerAllocationHistory(null);

        keyworkerAutoAllocationService.autoAllocate(TEST_AGENCY_ID);

        final var kwaArg = ArgumentCaptor.forClass(OffenderKeyworker.class);
        verify(keyworkerService, times(1)).allocate(kwaArg.capture());
    }

    @Test
    void testThatComplexOffenderFilterIsDisabledForMoorland() {
        final var prisonDetail = Prison.builder()
            .prisonId("MDI").capacityTier1(CAPACITY_TIER_1).capacityTier2(CAPACITY_TIER_2)
            .build();

        lenient().when(prisonSupportedService.getPrisonDetail("MDI")).thenReturn(prisonDetail);

        mockUnallocatedOffenders("MDI", Set.of("A12345", "G6415GD", "G8930UW"));
        mockKeyworkerPool(mockKeyworkers(1, 0, FULLY_ALLOCATED, CAPACITY_TIER_2), "MDI");
        mockPrisonerAllocationHistory(null);

        keyworkerAutoAllocationService.autoAllocate("MDI");

        final var kwaArg = ArgumentCaptor.forClass(OffenderKeyworker.class);
        verify(keyworkerService, times(3)).allocate(kwaArg.capture());
    }

    private void mockUnallocatedOffenders(final String prisonId, final Set<String> offenderNos) {
        final var offNos = offenderNos.toArray(new String[0]);

        final List<OffenderLocationDto> unallocatedOffenders = new ArrayList<>();

        for (var i = 0; i < offNos.length; i++) {
            unallocatedOffenders.add(KeyworkerTestHelper.getOffender(i + 1, prisonId, offNos[i]));
        }

        when(keyworkerService.getUnallocatedOffenders(eq(prisonId), isNull(), isNull())).thenReturn(unallocatedOffenders);
    }

    // Provides page of unallocated offenders (consistent with supplied pagination parameters)
    Page<OffenderLocationDto> pagedUnallocatedOffenders(final String prisonId, final Set<String> offenderNos, final long total, final long startId, final long limit) {
        final var offNos = offenderNos.toArray(new String[0]);

        final List<OffenderLocationDto> unallocatedOffenders = new ArrayList<>();

        for (long i = 0; i < Math.min(total, limit); i++) {
            final var idx = Long.valueOf(startId + i).intValue() - 1;

            unallocatedOffenders.add(KeyworkerTestHelper.getOffender(startId + i, prisonId, offNos[idx]));
        }

        return new Page<>(unallocatedOffenders, total, 0L, limit);
    }

    private List<KeyworkerDto> mockKeyworkers(final long total, final int minAllocations, final int maxAllocations, final int capacity) {
        final List<KeyworkerDto> mockKeyworkers;

        if (total > 0) {
            mockKeyworkers = KeyworkerTestHelper.getKeyworkers(total, minAllocations, maxAllocations, capacity);
        } else {
            mockKeyworkers = Collections.emptyList();
        }

        when(keyworkerService.getKeyworkersAvailableForAutoAllocation(anyString())).thenReturn(mockKeyworkers);

        return mockKeyworkers;
    }

    private List<KeyworkerDto> mockKeyworkers(final KeyworkerDto... keyworkers) {
        final List<KeyworkerDto> mockKeyworkers;

        if (keyworkers.length > 0) {
            mockKeyworkers = Arrays.asList(keyworkers);
        } else {
            mockKeyworkers = Collections.emptyList();
        }

        when(keyworkerService.getKeyworkersAvailableForAutoAllocation(anyString())).thenReturn(mockKeyworkers);

        return mockKeyworkers;
    }

    private void mockKeyworkerPool(final List<KeyworkerDto> keyworkers) {
        mockKeyworkerPool(keyworkers, TEST_AGENCY_ID);
    }

    private void mockKeyworkerPool(final List<KeyworkerDto> keyworkers, final String prisonId) {
        final var keyworkerPool =
            KeyworkerTestHelper.initKeyworkerPool(keyworkerService, prisonSupportedService, keyworkers, prisonId);

        when(keyworkerPoolFactory.getKeyworkerPool(prisonId, keyworkers)).thenReturn(keyworkerPool);
    }

    private void mockPrisonerAllocationHistory(final String offenderNo, final OffenderKeyworker... allocations) {
        final List<OffenderKeyworker> allocationHistory =
            (allocations == null) ? Collections.emptyList() : Arrays.asList(allocations);

        if (StringUtils.isBlank(offenderNo)) {
            when(keyworkerService.getAllocationHistoryForPrisoner(anyString())).thenReturn(allocationHistory);
        } else {
            when(keyworkerService.getAllocationHistoryForPrisoner(eq(offenderNo))).thenReturn(allocationHistory);
        }
    }

    private void mockKeyworkerAllocationHistory(final Long staffId, final OffenderKeyworker... allocations) {
        final List<OffenderKeyworker> allocationHistory =
            (allocations == null) ? Collections.emptyList() : Arrays.asList(allocations);

        when(keyworkerService.getAllocationsForKeyworker(eq(staffId))).thenReturn(allocationHistory);
    }
}
