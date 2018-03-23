package uk.gov.justice.digital.hmpps.keyworker.services;

import org.apache.commons.collections4.SetUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import uk.gov.justice.digital.hmpps.keyworker.dto.OffenderSummaryDto;
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
        List<OffenderSummaryDto> results = processor.filterByUnallocated(Collections.emptyList());

        assertThat(results).isNotNull();
        assertThat(results).isEmpty();
    }

    // When offender summary allocation filter processing requested with a list of 5 offender summary dtos
    // And none of the offenders has an active non-provisional allocation to a Key worker
    // Then response is same list of 5 offender summary dtos
    @Test
    public void testFilterByUnallocatedNoAllocations() {
        // Get some OffenderSummaryDto records
        List<OffenderSummaryDto> dtos = KeyworkerTestHelper.getOffenders(TEST_AGENCY, 5);

        Set<String> offNos = dtos.stream().map(OffenderSummaryDto::getOffenderNo).collect(Collectors.toSet());

        // Mock repository to return no active allocations for specified offender numbers.
        final OffenderKeyworker ok = OffenderKeyworker.builder()
                .offenderNo(offNos.iterator().next())
                .allocationType(AllocationType.PROVISIONAL)
                .build();
        when(repository.findByActiveAndOffenderNoIn(eq(true), anyCollectionOf(String.class))).thenReturn(Collections.singletonList(ok));

        // Invoke service
        List<OffenderSummaryDto> results = processor.filterByUnallocated(dtos);

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
        List<OffenderSummaryDto> dtos = KeyworkerTestHelper.getOffenders(TEST_AGENCY, 5);

        Set<String> offNos = dtos.stream().map(OffenderSummaryDto::getOffenderNo).collect(Collectors.toSet());

        // Mock repository to return active allocations for all offender numbers.
        List<OffenderKeyworker> allocs = KeyworkerTestHelper.getAllocations(TEST_AGENCY, offNos);

        when(repository.findByActiveAndOffenderNoIn(eq(true), anyCollectionOf(String.class))).thenReturn(allocs);

        // Invoke service
        List<OffenderSummaryDto> results = processor.filterByUnallocated(dtos);

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
        List<OffenderSummaryDto> dtos = KeyworkerTestHelper.getOffenders(TEST_AGENCY, 5);

        // Offenders with odd booking ids are allocated
        Set<String> allocatedOffNos = dtos.stream().filter(dto -> dto.getBookingId() % 2 != 0).map(OffenderSummaryDto::getOffenderNo).collect(Collectors.toSet());

        // So offenders with even booking ids are unallocated
        Set<String> unallocatedOffNos = dtos.stream().map(OffenderSummaryDto::getOffenderNo).filter(offNo -> !allocatedOffNos.contains(offNo)).collect(Collectors.toSet());

        // Mock repository to return active allocations for 3 offender numbers.
        List<OffenderKeyworker> allocs = KeyworkerTestHelper.getAllocations(TEST_AGENCY, allocatedOffNos);

        when(repository.findByActiveAndOffenderNoIn(eq(true), anyCollectionOf(String.class))).thenReturn(allocs);

        // Invoke service
        List<OffenderSummaryDto> results = processor.filterByUnallocated(dtos);

        // Verify
        assertThat(results.size()).isEqualTo(unallocatedOffNos.size());
        assertThat(results).extracting(OffenderSummaryDto::getOffenderNo).hasSameElementsAs(unallocatedOffNos);

        verify(repository, times(1)).findByActiveAndOffenderNoIn(eq(true), eq(SetUtils.union(allocatedOffNos, unallocatedOffNos)));
    }
}
