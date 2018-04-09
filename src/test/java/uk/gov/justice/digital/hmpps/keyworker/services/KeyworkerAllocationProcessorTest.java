package uk.gov.justice.digital.hmpps.keyworker.services;

import org.apache.commons.collections4.SetUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import uk.gov.justice.digital.hmpps.keyworker.dto.OffenderLocationDto;
import uk.gov.justice.digital.hmpps.keyworker.model.AllocationType;
import uk.gov.justice.digital.hmpps.keyworker.model.OffenderKeyworker;
import uk.gov.justice.digital.hmpps.keyworker.repository.OffenderKeyworkerRepository;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.anyCollectionOf;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class KeyworkerAllocationProcessorTest {
    public static final String TEST_AGENCY = "ABC";

    @Mock
    private OffenderKeyworkerRepository repository;

    @InjectMocks
    private KeyworkerAllocationProcessor processor;

    // When offender summary allocation filter processing requested with a 'null' dto list
    // Then NPE is thrown
    @Test(expected = NullPointerException.class)
    public void testFilterByUnallocatedNullInput() {
        processor.filterByUnallocated(null);
    }

    // When offender summary allocation filter processing requested with an empty dto list
    // Then response is an empty dto list
    @Test
    public void testFilterByUnallocatedEmptyInput() {
        List<OffenderLocationDto> results = processor.filterByUnallocated(Collections.emptyList());

        assertThat(results).isNotNull();
        assertThat(results).isEmpty();
    }

    // When offender summary allocation filter processing requested with a list of 5 offender summary dtos
    // And none of the offenders has an active non-provisional allocation to a Key worker
    // Then response is same list of 5 offender summary dtos
    @Test
    public void testFilterByUnallocatedNoAllocations() {
        // Get some OffenderSummaryDto records
        List<OffenderLocationDto> dtos = KeyworkerTestHelper.getOffenders(TEST_AGENCY, 5);

        Set<String> offNos = dtos.stream().map(OffenderLocationDto::getOffenderNo).collect(Collectors.toSet());

        // Mock remote to return no active allocations for specified offender numbers.
        final OffenderKeyworker ok = OffenderKeyworker.builder()
                .offenderNo(offNos.iterator().next())
                .allocationType(AllocationType.PROVISIONAL)
                .build();
        when(repository.findByActiveAndOffenderNoIn(eq(true), anyCollectionOf(String.class))).thenReturn(Collections.singletonList(ok));

        // Invoke service
        List<OffenderLocationDto> results = processor.filterByUnallocated(dtos);

        // Verify
        assertThat(results).isEqualTo(dtos);

        verify(repository, times(1)).findByActiveAndOffenderNoIn(eq(true), eq(offNos));
    }

    // When offender summary allocation filter processing requested with a list of 5 offender summary dtos
    // And all of the offenders have an active allocation to a Key worker
    // Then response is an empty list
    @Test
    public void testFilterByUnallocatedAllAllocated() {
        // Get some OffenderSummaryDto records
        List<OffenderLocationDto> dtos = KeyworkerTestHelper.getOffenders(TEST_AGENCY, 5);

        Set<String> offNos = dtos.stream().map(OffenderLocationDto::getOffenderNo).collect(Collectors.toSet());

        // Mock remote to return active allocations for all offender numbers.
        List<OffenderKeyworker> allocs = KeyworkerTestHelper.getAllocations(TEST_AGENCY, offNos);

        when(repository.findByActiveAndOffenderNoIn(eq(true), anyCollectionOf(String.class))).thenReturn(allocs);

        // Invoke service
        List<OffenderLocationDto> results = processor.filterByUnallocated(dtos);

        // Verify
        assertThat(results).isEmpty();

        verify(repository, times(1)).findByActiveAndOffenderNoIn(eq(true), eq(offNos));
    }

    // When offender summary allocation filter processing requested with a list of 5 offender summary dtos
    // And 3 of the offenders have an active allocation to a Key worker (so 2 do not)
    // Then response is a list of 2 offender summary dtos for the offenders who do not have an allocation
    @Test
    public void testFilterByUnallocatedSomeAllocated() {
        // Get some OffenderSummaryDto records
        List<OffenderLocationDto> dtos = KeyworkerTestHelper.getOffenders(TEST_AGENCY, 5);

        // Offenders with odd booking ids are allocated
        Set<String> allocatedOffNos = dtos.stream().filter(dto -> dto.getBookingId() % 2 != 0).map(OffenderLocationDto::getOffenderNo).collect(Collectors.toSet());

        // So offenders with even booking ids are unallocated
        Set<String> unallocatedOffNos = dtos.stream().map(OffenderLocationDto::getOffenderNo).filter(offNo -> !allocatedOffNos.contains(offNo)).collect(Collectors.toSet());

        // Mock remote to return active allocations for 3 offender numbers.
        List<OffenderKeyworker> allocs = KeyworkerTestHelper.getAllocations(TEST_AGENCY, allocatedOffNos);

        when(repository.findByActiveAndOffenderNoIn(eq(true), anyCollectionOf(String.class))).thenReturn(allocs);

        // Invoke service
        List<OffenderLocationDto> results = processor.filterByUnallocated(dtos);

        // Verify
        assertThat(results.size()).isEqualTo(unallocatedOffNos.size());
        assertThat(results).extracting(OffenderLocationDto::getOffenderNo).hasSameElementsAs(unallocatedOffNos);

        verify(repository, times(1)).findByActiveAndOffenderNoIn(eq(true), eq(SetUtils.union(allocatedOffNos, unallocatedOffNos)));
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
    public void testGetKeyworkerDetailsWhenAllocationRecentlyExpiredDueToOffenderRelease() {
        // A set of allocations for KW including an allocation for an offender that expired only 2 hours ago
        KeyWorkerAllocation expiredAllocation =
                getPreviousKeyworkerAutoAllocation(AGENCY_ID, BOOKING_ID, STAFF_ID, LocalDateTime.now().minusDays(7));

        expiredAllocation = expireAllocation(expiredAllocation, null, LocalDateTime.now().minusHours(2));

        List<KeyWorkerAllocation> kwAllocations = Arrays.asList(
                getPreviousKeyworkerAutoAllocation(AGENCY_ID, 5, STAFF_ID),
                getPreviousKeyworkerAutoAllocation(AGENCY_ID, 6, STAFF_ID),
                getPreviousKeyworkerAutoAllocation(AGENCY_ID, 7, STAFF_ID),
                getPreviousKeyworkerAutoAllocation(AGENCY_ID, 8, STAFF_ID),
                expiredAllocation
        );

        // Offender summary for offender who is subject of expired allocation
        OffenderSummary summary = getOffender(BOOKING_ID, AGENCY_ID, "A4565DF", false);

        // Expectations
        //  - repo to return a known Key worker with known number of active allocations
        //  - repo to return known set of KW allocations, including a recently expired allocation
        //  - booking service to return inactive booking summary for same booking in same agency
        int expectedAllocationCount = 4;

        when(repo.getKeyworkerDetails(STAFF_ID)).thenReturn(Optional.of(getKeyworker(STAFF_ID, expectedAllocationCount)));
        when(repo.getAllocationsForKeyworker(STAFF_ID)).thenReturn(kwAllocations);
        when(bookingService.getLatestBookingByBookingId(BOOKING_ID)).thenReturn(summary);

        // Call service method
        Keyworker keyworker = service.getKeyworkerDetails(STAFF_ID);

        // Assertions - number of allocations should include active allocations plus the recently expired allocation
        //              because offender who is subject of recently expired allocation does not have an active booking
        //              in any agency (either same or different)
        assertThat(keyworker.getNumberAllocated()).isEqualTo(expectedAllocationCount + 1);

        // Verifications
        verify(repo, atLeastOnce()).getKeyworkerDetails(eq(STAFF_ID));
        verify(repo, atLeastOnce()).getAllocationsForKeyworker(eq(STAFF_ID));
        verify(bookingService, atLeastOnce()).getLatestBookingByBookingId(eq(BOOKING_ID));
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
    public void testGetKeyworkerDetailsWhenAllocationExpiredDueToOffenderRelease() {
        // A set of allocations for KW including an allocation for an offender that expired 3 days ago
        KeyWorkerAllocation expiredAllocation =
                getPreviousKeyworkerAutoAllocation(AGENCY_ID, BOOKING_ID, STAFF_ID, LocalDateTime.now().minusDays(8));

        expiredAllocation = expireAllocation(expiredAllocation, null, LocalDateTime.now().minusDays(3));

        List<KeyWorkerAllocation> kwAllocations = Arrays.asList(
                getPreviousKeyworkerAutoAllocation(AGENCY_ID, 5, STAFF_ID),
                getPreviousKeyworkerAutoAllocation(AGENCY_ID, 6, STAFF_ID),
                getPreviousKeyworkerAutoAllocation(AGENCY_ID, 7, STAFF_ID),
                getPreviousKeyworkerAutoAllocation(AGENCY_ID, 8, STAFF_ID),
                expiredAllocation
        );

        // Expectations
        //  - repo to return a known Key worker with known number of active allocations
        //  - repo to return known set of KW allocations, including a recently expired allocation
        int expectedAllocationCount = 8;

        when(repo.getKeyworkerDetails(STAFF_ID)).thenReturn(Optional.of(getKeyworker(STAFF_ID, expectedAllocationCount)));
        when(repo.getAllocationsForKeyworker(STAFF_ID)).thenReturn(kwAllocations);

        // Call service method
        Keyworker keyworker = service.getKeyworkerDetails(STAFF_ID);

        // Assertions - number of allocations should include active allocations only, not the expired allocation as it
        //              expired before buffer period cut-off
        assertThat(keyworker.getNumberAllocated()).isEqualTo(expectedAllocationCount);

        // Verifications
        verify(repo, atLeastOnce()).getKeyworkerDetails(eq(STAFF_ID));
        verify(repo, atLeastOnce()).getAllocationsForKeyworker(eq(STAFF_ID));
        verify(bookingService, never()).getLatestBookingByBookingId(anyLong());
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
    public void testGetKeyworkerDetailsWhenAllocationRecentlyExpiredAndSameOffenderReallocated() {
        // A set of allocations for KW including an allocation for an offender that expired one day ago and including
        // an active allocation for same offender that was assigned two hours ago.
        KeyWorkerAllocation expiredAllocation =
                getPreviousKeyworkerAutoAllocation(AGENCY_ID, BOOKING_ID, STAFF_ID, LocalDateTime.now().minusDays(5));

        expiredAllocation = expireAllocation(expiredAllocation, null, LocalDateTime.now().minusDays(1));

        List<KeyWorkerAllocation> kwAllocations = Arrays.asList(
                getPreviousKeyworkerAutoAllocation(AGENCY_ID, 5, STAFF_ID),
                getPreviousKeyworkerAutoAllocation(AGENCY_ID, 6, STAFF_ID),
                getPreviousKeyworkerAutoAllocation(AGENCY_ID, 7, STAFF_ID),
                getPreviousKeyworkerAutoAllocation(AGENCY_ID, 8, STAFF_ID),
                expiredAllocation,
                getPreviousKeyworkerAutoAllocation(AGENCY_ID, BOOKING_ID, STAFF_ID, LocalDateTime.now().minusHours(2))
        );

        // Expectations
        //  - repo to return a known Key worker with known number of active allocations
        //  - repo to return known set of KW allocations, including a recently expired allocation
        int expectedAllocationCount = 5;

        when(repo.getKeyworkerDetails(STAFF_ID)).thenReturn(Optional.of(getKeyworker(STAFF_ID, expectedAllocationCount)));
        when(repo.getAllocationsForKeyworker(STAFF_ID)).thenReturn(kwAllocations);

        // Call service method
        Keyworker keyworker = service.getKeyworkerDetails(STAFF_ID);

        // Assertions - number of allocations should include active allocations only, not expired allocation because
        //              offender who is subject of expired allocation has been recalled and reallocated to same Key
        //              worker
        assertThat(keyworker.getNumberAllocated()).isEqualTo(expectedAllocationCount);

        // Verifications
        verify(repo, atLeastOnce()).getKeyworkerDetails(eq(STAFF_ID));
        verify(repo, atLeastOnce()).getAllocationsForKeyworker(eq(STAFF_ID));
        verify(bookingService, never()).getLatestBookingByBookingId(anyLong());
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
    public void testGetKeyworkerDetailsWhenAllocationRecentlyExpiredButOffenderActiveElsewhere() {
        // A set of allocations for KW including an allocation for an offender that expired only 5 hours ago
        KeyWorkerAllocation expiredAllocation =
                getPreviousKeyworkerAutoAllocation(AGENCY_ID, BOOKING_ID, STAFF_ID, LocalDateTime.now().minusDays(5));

        expiredAllocation = expireAllocation(expiredAllocation, null, LocalDateTime.now().minusHours(5));

        List<KeyWorkerAllocation> kwAllocations = Arrays.asList(
                getPreviousKeyworkerAutoAllocation(AGENCY_ID, 5, STAFF_ID),
                getPreviousKeyworkerAutoAllocation(AGENCY_ID, 6, STAFF_ID),
                getPreviousKeyworkerAutoAllocation(AGENCY_ID, 7, STAFF_ID),
                getPreviousKeyworkerAutoAllocation(AGENCY_ID, 8, STAFF_ID),
                expiredAllocation
        );

        // Offender summary for offender who is subject of expired allocation
        OffenderSummary summary = getOffender(785L, "SYI", "A4565DF", true);

        // Expectations
        //  - repo to return a known Key worker with known number of active allocations
        //  - repo to return known set of KW allocations, including a recently expired allocation
        //  - booking service to return booking summary that indicates offender has active booking in different agency
        int expectedAllocationCount = 4;

        when(repo.getKeyworkerDetails(STAFF_ID)).thenReturn(Optional.of(getKeyworker(STAFF_ID, expectedAllocationCount)));
        when(repo.getAllocationsForKeyworker(STAFF_ID)).thenReturn(kwAllocations);
        when(bookingService.getLatestBookingByBookingId(BOOKING_ID)).thenReturn(summary);

        // Call service method
        Keyworker keyworker = service.getKeyworkerDetails(STAFF_ID);

        // Assertions - number of allocations should include active allocations only, not expired allocation because
        //              offender who is subject of expired allocation now has an active booking at a different agency
        //              and is no longer eligible to be assigned to the same Key worker.
        assertThat(keyworker.getNumberAllocated()).isEqualTo(expectedAllocationCount);

        // Verifications
        verify(repo, atLeastOnce()).getKeyworkerDetails(eq(STAFF_ID));
        verify(repo, atLeastOnce()).getAllocationsForKeyworker(eq(STAFF_ID));
        verify(bookingService, atLeastOnce()).getLatestBookingByBookingId(eq(BOOKING_ID));
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
    public void testGetKeyworkerDetailsWhenAllocationRecentlyExpiredAndOffenderActiveButUnallocated() {
        // A set of allocations for KW including an allocation for an offender that expired over a day ago
        KeyWorkerAllocation expiredAllocation =
                getPreviousKeyworkerAutoAllocation(AGENCY_ID, BOOKING_ID, STAFF_ID, LocalDateTime.now().minusDays(7));

        expiredAllocation = expireAllocation(expiredAllocation, null, LocalDateTime.now().minusHours(30));

        List<KeyWorkerAllocation> kwAllocations = Arrays.asList(
                getPreviousKeyworkerAutoAllocation(AGENCY_ID, 5, STAFF_ID),
                getPreviousKeyworkerAutoAllocation(AGENCY_ID, 6, STAFF_ID),
                getPreviousKeyworkerAutoAllocation(AGENCY_ID, 7, STAFF_ID),
                getPreviousKeyworkerAutoAllocation(AGENCY_ID, 8, STAFF_ID),
                expiredAllocation
        );

        // Offender summary for offender who is subject of expired allocation
        OffenderSummary summary = getOffender(98745L, AGENCY_ID, "A9876BF", true);

        // Expectations
        //  - repo to return a known Key worker with known number of active allocations
        //  - repo to return known set of KW allocations, including a recently expired allocation
        //  - booking service to return booking summary that indicates offender has active booking in same agency
        //  - repo to return no active allocation to any Key worker for the offender (they are unallocated)
        int expectedAllocationCount = 4;

        when(repo.getKeyworkerDetails(STAFF_ID)).thenReturn(Optional.of(getKeyworker(STAFF_ID, expectedAllocationCount)));
        when(repo.getAllocationsForKeyworker(STAFF_ID)).thenReturn(kwAllocations);
        when(bookingService.getLatestBookingByBookingId(BOOKING_ID)).thenReturn(summary);
        when(repo.getCurrentAllocationForOffenderBooking(summary.getBookingId())).thenReturn(Optional.empty());

        // Call service method
        Keyworker keyworker = service.getKeyworkerDetails(STAFF_ID);

        // Assertions - number of allocations should include active allocations plus the recently expired allocation
        //              because offender who is subject of recently expired allocation has an active booking in same
        //              agency but has not yet been allocated to another Key worker
        assertThat(keyworker.getNumberAllocated()).isEqualTo(expectedAllocationCount + 1);

        // Verifications
        verify(repo, atLeastOnce()).getKeyworkerDetails(eq(STAFF_ID));
        verify(repo, atLeastOnce()).getAllocationsForKeyworker(eq(STAFF_ID));
        verify(bookingService, atLeastOnce()).getLatestBookingByBookingId(eq(BOOKING_ID));
        verify(repo, atLeastOnce()).getCurrentAllocationForOffenderBooking(eq(summary.getBookingId()));
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
    public void testGetKeyworkerDetailsWhenAllocationRecentlyExpiredAndOffenderAllocatedAnotherKeyworker() {
        // A set of allocations for KW including an allocation for an offender that expired a few hours ago and an
        // active allocation for same offender to different Key worker in same agency that was assigned an hour ago
        KeyWorkerAllocation expiredAllocation =
                getPreviousKeyworkerAutoAllocation(AGENCY_ID, BOOKING_ID, STAFF_ID, LocalDateTime.now().minusDays(7));

        expiredAllocation = expireAllocation(expiredAllocation, null, LocalDateTime.now().minusHours(9));

        Long newBookingId = 98754L;

        KeyWorkerAllocation newAllocation =
                getPreviousKeyworkerAutoAllocation(AGENCY_ID, newBookingId, ANOTHER_STAFF_ID, LocalDateTime.now().minusHours(1));

        List<KeyWorkerAllocation> kwAllocations = Arrays.asList(
                getPreviousKeyworkerAutoAllocation(AGENCY_ID, 5, STAFF_ID),
                getPreviousKeyworkerAutoAllocation(AGENCY_ID, 6, STAFF_ID),
                getPreviousKeyworkerAutoAllocation(AGENCY_ID, 7, STAFF_ID),
                getPreviousKeyworkerAutoAllocation(AGENCY_ID, 8, STAFF_ID),
                expiredAllocation,
                newAllocation
        );

        // Offender summary for offender who is subject of expired allocation
        OffenderSummary summary = getOffender(newBookingId, AGENCY_ID, "A9876BF", true);

        // Expectations
        //  - repo to return a known Key worker with known number of active allocations
        //  - repo to return known set of KW allocations, including a recently expired allocation
        //  - booking service to return booking summary that indicates offender has active booking in same agency
        //  - repo to return no active allocation to any Key worker for the offender (they are unallocated)
        int expectedAllocationCount = 5;

        when(repo.getKeyworkerDetails(STAFF_ID)).thenReturn(Optional.of(getKeyworker(STAFF_ID, expectedAllocationCount)));
        when(repo.getAllocationsForKeyworker(STAFF_ID)).thenReturn(kwAllocations);
        when(bookingService.getLatestBookingByBookingId(BOOKING_ID)).thenReturn(summary);
        when(repo.getCurrentAllocationForOffenderBooking(summary.getBookingId())).thenReturn(Optional.of(newAllocation));

        // Call service method
        Keyworker keyworker = service.getKeyworkerDetails(STAFF_ID);

        // Assertions - number of allocations should include active allocations only, not recently expired allocation
        //              because offender who is subject of recently expired allocation is now allocated to a different
        //              Key worker.
        assertThat(keyworker.getNumberAllocated()).isEqualTo(expectedAllocationCount);

        // Verifications
        verify(repo, atLeastOnce()).getKeyworkerDetails(eq(STAFF_ID));
        verify(repo, atLeastOnce()).getAllocationsForKeyworker(eq(STAFF_ID));
        verify(bookingService, atLeastOnce()).getLatestBookingByBookingId(eq(BOOKING_ID));
        verify(repo, atLeastOnce()).getCurrentAllocationForOffenderBooking(eq(summary.getBookingId()));
    }
}
