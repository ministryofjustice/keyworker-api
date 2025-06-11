package uk.gov.justice.digital.hmpps.keyworker.services;

import com.google.common.collect.Sets;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.digital.hmpps.keyworker.dto.OffenderLocationDto;
import uk.gov.justice.digital.hmpps.keyworker.model.AllocationType;
import uk.gov.justice.digital.hmpps.keyworker.model.LegacyKeyworkerAllocation;
import uk.gov.justice.digital.hmpps.keyworker.repository.LegacyKeyworkerAllocationRepository;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.anyCollection;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KeyworkerAllocationProcessorTest {
    private static final String TEST_AGENCY = "ABC";

    @Mock
    private LegacyKeyworkerAllocationRepository repository;

    @InjectMocks
    private KeyworkerAllocationProcessor processor;

    // When offender summary allocation filter processing requested with a 'null' dto list
    // Then NPE is thrown
    @Test
    void testFilterByUnallocatedNullInput() {
        assertThatThrownBy(() -> processor.filterByUnallocated(null)).isInstanceOf(NullPointerException.class);
    }

    // When offender summary allocation filter processing requested with an empty dto list
    // Then response is an empty dto list
    @Test
    void testFilterByUnallocatedEmptyInput() {
        final var results = processor.filterByUnallocated(Collections.emptyList());

        assertThat(results).isNotNull();
        assertThat(results).isEmpty();
    }

    // When offender summary allocation filter processing requested with a list of 5 offender summary dtos
    // And none of the offenders has an active non-provisional allocation to a Key worker
    // Then response is same list of 5 offender summary dtos
    @Test
    void testFilterByUnallocatedNoAllocations() {
        // Get some OffenderSummaryDto records
        final var dtos = KeyworkerTestHelper.getOffenders(TEST_AGENCY, 5);

        final var offNos = dtos.stream().map(OffenderLocationDto::getOffenderNo).collect(Collectors.toSet());

        // Mock remote to return no active allocations for specified offender numbers.
        final var ok = LegacyKeyworkerAllocation.builder()
            .offenderNo(offNos.iterator().next())
            .allocationType(AllocationType.PROVISIONAL)
            .build();
        when(repository.findByActiveAndPersonIdentifierIn(eq(true), anyCollection())).thenReturn(Collections.singletonList(ok));

        // Invoke service
        final var results = processor.filterByUnallocated(dtos);

        // Verify
        assertThat(results).isEqualTo(dtos);

        verify(repository).findByActiveAndPersonIdentifierIn(eq(true), eq(offNos));
    }

    // When offender summary allocation filter processing requested with a list of 5 offender summary dtos
    // And all of the offenders have an active allocation to a Key worker
    // Then response is an empty list
    @Test
    void testFilterByUnallocatedAllAllocated() {
        // Get some OffenderSummaryDto records
        final var dtos = KeyworkerTestHelper.getOffenders(TEST_AGENCY, 5);

        final var offNos = dtos.stream().map(OffenderLocationDto::getOffenderNo).collect(Collectors.toSet());

        // Mock remote to return active allocations for all offender numbers.
        final var allocs = KeyworkerTestHelper.getAllocations(TEST_AGENCY, offNos);

        when(repository.findByActiveAndPersonIdentifierIn(eq(true), anyCollection())).thenReturn(allocs);

        // Invoke service
        final var results = processor.filterByUnallocated(dtos);

        // Verify
        assertThat(results).isEmpty();

        verify(repository).findByActiveAndPersonIdentifierIn(eq(true), eq(offNos));
    }

    //should be resilient if duplicate allocations exist
    @Test
    void testFilterByUnallocatedHandlesDuplicateActiveAllocations() {
        // Get some OffenderSummaryDto records
        final var dtos = KeyworkerTestHelper.getOffenders(TEST_AGENCY, 5);

        final var offNos = dtos.stream().map(OffenderLocationDto::getOffenderNo).collect(Collectors.toSet());

        // Mock remote to return active allocations for all offender numbers.
        final var allocs = KeyworkerTestHelper.getAllocations(TEST_AGENCY, offNos);

        allocs.add(allocs.get(0));

        when(repository.findByActiveAndPersonIdentifierIn(eq(true), anyCollection())).thenReturn(allocs);

        // Invoke service
        final var results = processor.filterByUnallocated(dtos);

        // Verify
        assertThat(results).isEmpty();

        verify(repository).findByActiveAndPersonIdentifierIn(eq(true), eq(offNos));
    }

    // When offender summary allocation filter processing requested with a list of 5 offender summary dtos
    // And 3 of the offenders have an active allocation to a Key worker (so 2 do not)
    // Then response is a list of 2 offender summary dtos for the offenders who do not have an allocation
    @Test
    void testFilterByUnallocatedSomeAllocated() {
        // Get some OffenderSummaryDto records
        final var dtos = KeyworkerTestHelper.getOffenders(TEST_AGENCY, 5);

        // Offenders with odd booking ids are allocated
        final var allocatedOffNos = dtos.stream().filter(dto -> dto.getBookingId() % 2 != 0).map(OffenderLocationDto::getOffenderNo).collect(Collectors.toSet());

        // So offenders with even booking ids are unallocated
        final var unallocatedOffNos = dtos.stream().map(OffenderLocationDto::getOffenderNo).filter(offNo -> !allocatedOffNos.contains(offNo)).collect(Collectors.toSet());

        // Mock remote to return active allocations for 3 offender numbers.
        final var allocs = KeyworkerTestHelper.getAllocations(TEST_AGENCY, allocatedOffNos);

        when(repository.findByActiveAndPersonIdentifierIn(eq(true), anyCollection())).thenReturn(allocs);

        // Invoke service
        final var results = processor.filterByUnallocated(dtos);

        // Verify
        assertThat(results.size()).isEqualTo(unallocatedOffNos.size());
        assertThat(results).extracting(OffenderLocationDto::getOffenderNo).hasSameElementsAs(unallocatedOffNos);

        verify(repository).findByActiveAndPersonIdentifierIn(true, Sets.union(allocatedOffNos, unallocatedOffNos));
    }

    @Test
    void testDecorateAllocatedCanHandleDuplicates() {
        final var OFFENDER_NO = "A12345";

        final var offenderKeyworkers = List.of(
            LegacyKeyworkerAllocation.builder().offenderNo(OFFENDER_NO).build()
        );

        final var offenderLocations = List.of(
            OffenderLocationDto.builder().offenderNo(OFFENDER_NO).firstName("Bob").lastName("Doe").assignedLivingUnitDesc("MDI").build(),
            OffenderLocationDto.builder().offenderNo(OFFENDER_NO).firstName("Bob").lastName("Doe").assignedLivingUnitDesc("MDI").build()
        );

        final var keyworkerAllocations = processor.decorateAllocated(offenderKeyworkers, offenderLocations);

        assertThat(keyworkerAllocations.size()).isEqualTo(1);
        assertThat(keyworkerAllocations)
            .extracting("offenderNo", "firstName", "lastName", "internalLocationDesc")
            .contains(Tuple.tuple(OFFENDER_NO, "Bob", "Doe", "MDI"));
    }
}
