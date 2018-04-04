package uk.gov.justice.digital.hmpps.keyworker.services;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.Validate;
import uk.gov.justice.digital.hmpps.keyworker.dto.BasicKeyworkerDto;
import uk.gov.justice.digital.hmpps.keyworker.dto.KeyworkerDto;
import uk.gov.justice.digital.hmpps.keyworker.dto.OffenderLocationDto;
import uk.gov.justice.digital.hmpps.keyworker.dto.StaffLocationRoleDto;
import uk.gov.justice.digital.hmpps.keyworker.model.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.when;

public class KeyworkerTestHelper {
    public static final int CAPACITY_TIER_1 = 6;
    public static final int CAPACITY_TIER_2 = 9;
    public static final int FULLY_ALLOCATED = CAPACITY_TIER_2;

    private static int offenderNumber = 1110;
    private static long bookingId = 0;

    public static String getNextOffenderNo() {
        return String.format("A%4dAA", ++offenderNumber);
    }

    public static Set<String> getNextOffenderNo(int count) {
        Set<String> offNos = new HashSet<>();

        for (int i = 0; i < count; i++) {
            offNos.add(String.format("A%4dAA", ++offenderNumber));
        }

        return offNos;
    }

    private static long getNextBookingId() {
        return ++bookingId;
    }

    public static void verifyException(Throwable thrown, Class<? extends Throwable> expectedException, String expectedMessage) {
        assertThat(thrown).isInstanceOf(expectedException).hasMessage(expectedMessage);
    }

    // Provides a Key worker with specified staff id and number of allocations
    public static KeyworkerDto getKeyworker(long staffId, int numberOfAllocations, int capacity) {
        return KeyworkerDto.builder()
                .staffId(staffId)
                .numberAllocated(numberOfAllocations)
                .capacity(capacity)
                .firstName(RandomStringUtils.randomAscii(35))
                .lastName(RandomStringUtils.randomAscii(35))
                .autoAllocationAllowed(true)
                .build();
    }

    // Provides list of Key workers with varying number of allocations (within specified range)
    public static List<KeyworkerDto> getKeyworkers(long total, int minAllocations, int maxAllocations, int capacity) {
        List<KeyworkerDto> keyworkers = new ArrayList<>();

        for (long i = 1; i <= total; i++) {
            keyworkers.add(KeyworkerDto.builder()
                    .staffId(i)
                    .numberAllocated(RandomUtils.nextInt(minAllocations, maxAllocations + 1))
                    .capacity(capacity)
                    .build());
        }

        return keyworkers;
    }

    public static StaffLocationRoleDto getStaffLocationRoleDto(long staffId) {
        return StaffLocationRoleDto.builder()
                .staffId(staffId)
                .firstName("First")
                .lastName("Last")
                .agencyId("LEI")
                .agencyDescription("LEEDS")
                .fromDate(LocalDate.of(2018, Month.FEBRUARY, 28))
                .position("AO")
                .positionDescription("Admin Officer")
                .role("KW")
                .roleDescription("Key Worker")
                .scheduleType("FT")
                .scheduleTypeDescription("Full Time")
                .build();
    }

    public static void verifyBasicKeyworkerDto(BasicKeyworkerDto keyworkerDetails, long staffId, String firstName, String lastName) {
        assertThat(keyworkerDetails.getStaffId()).isEqualTo(staffId);
        assertThat(keyworkerDetails.getFirstName()).isEqualTo(firstName);
        assertThat(keyworkerDetails.getLastName()).isEqualTo(lastName);
    }

    public static void verifyKeyworkerDto(long staffId, Integer capacity, Integer allocations, KeyworkerStatus status, KeyworkerDto keyworkerDetails) {
        assertThat(keyworkerDetails.getStaffId()).isEqualTo(staffId);
        assertThat(keyworkerDetails.getNumberAllocated()).isEqualTo(allocations);
        assertThat(keyworkerDetails.getFirstName()).isEqualTo("First");
        assertThat(keyworkerDetails.getLastName()).isEqualTo("Last");
        assertThat(keyworkerDetails.getAgencyId()).isEqualTo("LEI");
        assertThat(keyworkerDetails.getAgencyDescription()).isEqualTo("LEEDS");
        assertThat(keyworkerDetails.getCapacity()).isEqualTo(capacity);
        assertThat(keyworkerDetails.getScheduleType()).isEqualTo("Full Time");
        assertThat(keyworkerDetails.getStatus()).isEqualTo(status);
    }

    private static OffenderLocationDto getOffender(long bookingId, String prisonId) {
        return getOffender(bookingId, prisonId, getNextOffenderNo(), true);
    }

    public static OffenderLocationDto getOffender(long bookingId, String prisonId, String offenderNo, boolean currentlyInPrison) {
        return OffenderLocationDto.builder()
                .bookingId(bookingId)
                .agencyId(prisonId)
                .offenderNo(offenderNo)
                .lastName("Testlastname")
                .build();
    }

    public static List<OffenderLocationDto> getOffenders(String prisonId, long total) {
        Validate.notBlank(prisonId);
        Validate.isTrue(total > 0);

        List<OffenderLocationDto> dtos = new ArrayList<>();

        for (long i = 1; i <= total; i++) {
            dtos.add(getOffender(getNextBookingId(), prisonId));
        }

        return dtos;
    }

    public static void verifyAutoAllocation(OffenderKeyworker kwAlloc, String prisonId, String offenderNo, long staffId) {
        verifyNewAllocation(kwAlloc, prisonId, offenderNo, staffId);

        assertThat(kwAlloc.getAllocationType()).isEqualTo(AllocationType.PROVISIONAL);
        assertThat(kwAlloc.getAllocationReason()).isEqualTo(AllocationReason.AUTO);
    }

    public static void verifyNewAllocation(OffenderKeyworker kwAlloc, String prisonId, String offenderNo, long staffId) {
        assertThat(kwAlloc.getOffenderNo()).isEqualTo(offenderNo);
        assertThat(kwAlloc.getStaffId()).isEqualTo(staffId);
        assertThat(kwAlloc.getAllocationType()).isNotNull();
        assertThat(kwAlloc.getAllocationReason()).isNotNull();
        assertThat(kwAlloc.getAssignedDateTime()).isNotNull();
        assertThat(kwAlloc.isActive()).isTrue();
        assertThat(kwAlloc.getPrisonId()).isEqualTo(prisonId);
        assertThat(kwAlloc.getDeallocationReason()).isNull();
        assertThat(kwAlloc.getExpiryDateTime()).isNull();
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
    public static OffenderKeyworker getPreviousKeyworkerAutoAllocation(String prisonId, String offenderNo, long staffId) {
        return getPreviousKeyworkerAutoAllocation(prisonId, offenderNo, staffId, LocalDateTime.now().minusDays(7));
    }

    // Provides a previous Key worker allocation between specified offender and Key worker, assigned at specified datetime.
    public static OffenderKeyworker getPreviousKeyworkerAutoAllocation(String prisonId, String offenderNo, long staffId, LocalDateTime assigned) {
        Validate.notNull(assigned, "Allocation must have assigned datetime.");

        return OffenderKeyworker.builder()
                .prisonId(prisonId)
                .offenderNo(offenderNo)
                .staffId(staffId)
                .active(true)
                .assignedDateTime(assigned)
                .allocationType(AllocationType.AUTO)
                .allocationReason(AllocationReason.AUTO)
                .build();
    }

    // Expires a Key worker allocation using specified reason and expiry datetime.
    public static OffenderKeyworker expireAllocation(OffenderKeyworker allocation, DeallocationReason reason, LocalDateTime expiry) {
        Validate.notNull(allocation, "Allocation to expire must be specified.");
        Validate.notNull(expiry, "Expiry datetime must be specified.");

        return OffenderKeyworker.builder()
                .prisonId(allocation.getPrisonId())
                .offenderNo(allocation.getOffenderNo())
                .staffId(allocation.getStaffId())
                .active(false)
                .deallocationReason(reason)
                .expiryDateTime(expiry)
                .build();
    }

    public static List<OffenderKeyworker> getAllocations(String prisonId, Set<String> offNos) {
        Validate.notBlank(prisonId);
        Validate.notEmpty(offNos);

        List<OffenderKeyworker> allocs = new ArrayList<>();

        offNos.forEach(offNo -> allocs.add(getPreviousKeyworkerAutoAllocation(prisonId, offNo, 1)));

        return allocs;
    }
}
