package uk.gov.justice.digital.hmpps.keyworker.services;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.Validate;
import uk.gov.justice.digital.hmpps.keyworker.dto.KeyworkerDto;
import uk.gov.justice.digital.hmpps.keyworker.dto.OffenderSummaryDto;
import uk.gov.justice.digital.hmpps.keyworker.model.AllocationReason;
import uk.gov.justice.digital.hmpps.keyworker.model.AllocationType;
import uk.gov.justice.digital.hmpps.keyworker.model.DeallocationReason;
import uk.gov.justice.digital.hmpps.keyworker.model.OffenderKeyworker;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.when;

public class KeyworkerTestHelper {
    public static final int CAPACITY_TIER_1 = 6;
    public static final int CAPACITY_TIER_2 = 9;
    public static final int FULLY_ALLOCATED = CAPACITY_TIER_2;

    public static void verifyException(Throwable thrown, Class<? extends Throwable> expectedException, String expectedMessage) {
        assertThat(thrown).isInstanceOf(expectedException).hasMessage(expectedMessage);
    }

    // Provides a Key worker with specified staff id and number of allocations
    public static KeyworkerDto getKeyworker(long staffId, int numberOfAllocations) {
        return KeyworkerDto.builder()
                .staffId(staffId)
                .numberAllocated(numberOfAllocations)
                .firstName(RandomStringUtils.randomAscii(35))
                .lastName(RandomStringUtils.randomAscii(35))
                .build();
    }

    // Provides list of Key workers with varying number of allocations (within specified range)
    public static List<KeyworkerDto> getKeyworkers(long total, int minAllocations, int maxAllocations) {
        List<KeyworkerDto> keyworkers = new ArrayList<>();

        for (long i = 1; i <= total; i++) {
            keyworkers.add(KeyworkerDto.builder()
                    .staffId(i)
                    .numberAllocated(RandomUtils.nextInt(minAllocations, maxAllocations + 1))
                    .build());
        }

        return keyworkers;
    }

    public static OffenderSummaryDto getOffender(long bookingId, String agencyId) {
        return getOffender(bookingId, agencyId, null, true);
    }

    public static OffenderSummaryDto getOffender(long bookingId, String agencyId, String offenderNo, boolean currentlyInPrison) {
        return OffenderSummaryDto.builder()
                .bookingId(bookingId)
                .agencyLocationId(agencyId)
                .offenderNo(offenderNo)
                .currentlyInPrison(currentlyInPrison ? "Y" : "N")
                .build();
    }

    public static void verifyAutoAllocation(OffenderKeyworker kwAlloc, String offenderNo, long staffId) {
        assertThat(kwAlloc.getOffenderNo()).isEqualTo(offenderNo);
        assertThat(kwAlloc.getStaffId()).isEqualTo(staffId);
        assertThat(kwAlloc.getAllocationType()).isEqualTo(AllocationType.AUTO);
        assertThat(kwAlloc.getAllocationReason()).isEqualTo(AllocationReason.AUTO);
    }

    public static void mockPrisonerAllocationHistory(KeyworkerService keyworkerService,
                                                     OffenderKeyworker... allocations) {
        List<OffenderKeyworker> allocationHistory =
                (allocations == null) ? Collections.emptyList() : Arrays.asList(allocations);

        when(keyworkerService.getAllocationHistoryForPrisoner(anyString())).thenReturn(allocationHistory);
    }

    public static KeyworkerPool initKeyworkerPool(KeyworkerService keyworkerService,
                                                  Collection<KeyworkerDto> keyworkers, Collection<Integer> capacityTiers) {
        KeyworkerPool keyworkerPool = new KeyworkerPool(keyworkers, capacityTiers);

        keyworkerPool.setKeyworkerService(keyworkerService);

        return keyworkerPool;
    }

    // Provides a previous Key worker allocation between specified offender and Key worker with an assigned datetime 7
    // days prior to now.
    public static OffenderKeyworker getPreviousKeyworkerAutoAllocation(String agencyId, String offenderNo, long staffId) {
        return getPreviousKeyworkerAutoAllocation(agencyId, offenderNo, staffId, LocalDateTime.now().minusDays(7));
    }

    // Provides a previous Key worker allocation between specified offender and Key worker, assigned at specified datetime.
    public static OffenderKeyworker getPreviousKeyworkerAutoAllocation(String agencyId, String offenderNo, long staffId, LocalDateTime assigned) {
        Validate.notNull(assigned, "Allocation must have assigned datetime.");

        return OffenderKeyworker.builder()
                .agencyId(agencyId)
                .offenderNo(offenderNo)
                .staffId(staffId)
                .active(true)
                .assignedDateTime(assigned)
                .allocationType(AllocationType.AUTO)
                .build();
    }

    // Expires a Key worker allocation using specified reason and expiry datetime.
    public static OffenderKeyworker expireAllocation(OffenderKeyworker allocation, DeallocationReason reason, LocalDateTime expiry) {
        Validate.notNull(allocation, "Allocation to expire must be specified.");
        Validate.notNull(expiry, "Expiry datetime must be specified.");

        return OffenderKeyworker.builder()
                .agencyId(allocation.getAgencyId())
                .offenderNo(allocation.getOffenderNo())
                .staffId(allocation.getStaffId())
                .active(false)
                .deallocationReason(reason)
                .expiryDateTime(expiry)
                .build();
    }
}
