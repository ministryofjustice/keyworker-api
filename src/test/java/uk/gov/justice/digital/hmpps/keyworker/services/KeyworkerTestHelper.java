package uk.gov.justice.digital.hmpps.keyworker.services;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.Validate;
import uk.gov.justice.digital.hmpps.keyworker.dto.BasicKeyworkerDto;
import uk.gov.justice.digital.hmpps.keyworker.dto.KeyworkerAllocationDetailsDto;
import uk.gov.justice.digital.hmpps.keyworker.dto.KeyworkerDto;
import uk.gov.justice.digital.hmpps.keyworker.dto.OffenderLocationDto;
import uk.gov.justice.digital.hmpps.keyworker.dto.PrisonerDetail;
import uk.gov.justice.digital.hmpps.keyworker.dto.StaffLocationRoleDto;
import uk.gov.justice.digital.hmpps.keyworker.model.AllocationReason;
import uk.gov.justice.digital.hmpps.keyworker.model.AllocationType;
import uk.gov.justice.digital.hmpps.keyworker.model.KeyworkerStatus;
import uk.gov.justice.digital.hmpps.keyworker.model.OffenderKeyworker;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.when;
import static uk.gov.justice.digital.hmpps.keyworker.utils.ReferenceDataHelper.allocationReason;

class KeyworkerTestHelper {
    public static final int CAPACITY_TIER_1 = 6;
    public static final int CAPACITY_TIER_2 = 9;
    public static final int FULLY_ALLOCATED = CAPACITY_TIER_2;

    private static int offenderNumber = 1110;
    private static long bookingId = 0;

    public static String getNextOffenderNo() {
        return String.format("A%4dAA", ++offenderNumber);
    }

    public static Set<String> getNextOffenderNo(final int count) {
        final Set<String> offNos = new HashSet<>();

        for (var i = 0; i < count; i++) {
            offNos.add(String.format("A%4dAA", ++offenderNumber));
        }

        return offNos;
    }

    private static long getNextBookingId() {
        return ++bookingId;
    }

    public static void verifyException(final Throwable thrown, final Class<? extends Throwable> expectedException, final String expectedMessage) {
        assertThat(thrown).isInstanceOf(expectedException).hasMessage(expectedMessage);
    }

    // Provides a Key worker with specified staff id and number of allocations
    public static KeyworkerDto getKeyworker(final long staffId, final int numberOfAllocations, final int capacity) {
        return KeyworkerDto.builder()
                .staffId(staffId)
                .numberAllocated(numberOfAllocations)
                .capacity(capacity)
                .firstName(RandomStringUtils.randomAscii(35))
                .lastName(RandomStringUtils.randomAscii(35))
                .autoAllocationAllowed(true)
                .build();
    }

    // Provides a Key worker with specified staff id and number of allocations
    public static KeyworkerAllocationDetailsDto getKeyworkerAllocations(final long staffId, final String offenderNo, final String prisonId, final LocalDateTime assignedTime) {
        return KeyworkerAllocationDetailsDto.builder()
                .staffId(staffId)
                .offenderNo(offenderNo)
                .agencyId(prisonId)
                .allocationType(AllocationType.MANUAL)
                .assigned(assignedTime)
                .internalLocationDesc("A-1-3")
                .firstName(RandomStringUtils.randomAscii(35))
                .lastName(RandomStringUtils.randomAscii(35))
                .build();
    }

    // Provides a Key worker with specified staff id and number of allocations
    public static BasicKeyworkerDto getKeyworker(final long staffId) {
        return BasicKeyworkerDto.builder()
                .staffId(staffId)
                .firstName(RandomStringUtils.randomAscii(35))
                .lastName(RandomStringUtils.randomAscii(35))
                .build();
    }

    public static List<KeyworkerDto> getKeyworkers(final long total, final int minAllocations, final int maxAllocations, final int capacity) {
        return getKeyworkers(total, minAllocations, maxAllocations, capacity, null);
    }

    // Provides list of Key workers with varying number of allocations (within specified range)
    private static List<KeyworkerDto> getKeyworkers(final long total, final int minAllocations, final int maxAllocations, final int capacity, final String agencyId) {
        final List<KeyworkerDto> keyworkers = new ArrayList<>();

        for (long i = 1; i <= total; i++) {
            keyworkers.add(KeyworkerDto.builder()
                    .staffId(i)
                    .numberAllocated(RandomUtils.nextInt(minAllocations, maxAllocations + 1))
                    .agencyId(agencyId)
                    .capacity(capacity)
                    .build());
        }
        return keyworkers;
    }

    public static StaffLocationRoleDto getStaffLocationRoleDto(final long staffId) {
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

    public static StaffLocationRoleDto getBasicVersionOfStaffLocationRoleDto(final long staffId) {
        return StaffLocationRoleDto.builder()
                .staffId(staffId)
                .firstName("First")
                .lastName("Last")
                .build();
    }

    public static void verifyBasicKeyworkerDto(final BasicKeyworkerDto keyworkerDetails, final long staffId, final String firstName, final String lastName) {
        assertThat(keyworkerDetails.getStaffId()).isEqualTo(staffId);
        assertThat(keyworkerDetails.getFirstName()).isEqualTo(firstName);
        assertThat(keyworkerDetails.getLastName()).isEqualTo(lastName);
    }

    public static void verifyKeyworkerDto(final long staffId, final Integer capacity, final Integer allocations, final KeyworkerStatus status, final KeyworkerDto keyworkerDetails, final LocalDate activeDate) {
        assertThat(keyworkerDetails.getStaffId()).isEqualTo(staffId);
        assertThat(keyworkerDetails.getNumberAllocated()).isEqualTo(allocations);
        assertThat(keyworkerDetails.getFirstName()).isEqualTo("First");
        assertThat(keyworkerDetails.getLastName()).isEqualTo("Last");
        assertThat(keyworkerDetails.getAgencyId()).isEqualTo("LEI");
        assertThat(keyworkerDetails.getAgencyDescription()).isEqualTo("LEEDS");
        assertThat(keyworkerDetails.getCapacity()).isEqualTo(capacity);
        assertThat(keyworkerDetails.getScheduleType()).isEqualTo("Full Time");
        assertThat(keyworkerDetails.getStatus()).isEqualTo(status);
        assertThat(keyworkerDetails.getActiveDate()).isEqualTo(activeDate);
    }

    private static OffenderLocationDto getOffender(final long bookingId, final String prisonId) {
        return getOffender(bookingId, prisonId, getNextOffenderNo());
    }

    public static OffenderLocationDto getOffender(final long bookingId, final String prisonId, final String offenderNo) {
        return OffenderLocationDto.builder()
                .bookingId(bookingId)
                .agencyId(prisonId)
                .offenderNo(offenderNo)
                .lastName("Testlastname")
                .firstName("TestFirstname")
                .build();
    }

    public static PrisonerDetail getPrisonerDetail(final long bookingId, final String prisonId, final String offenderNo, final boolean currentlyInPrison, final String internalLocation) {
        return PrisonerDetail.builder()
                .latestBookingId(bookingId)
                .latestLocationId(prisonId)
                .currentlyInPrison(currentlyInPrison ? "Y" : "N")
                .offenderNo(offenderNo)
                .lastName("Testlastname")
                .firstName("TestFirstname")
                .internalLocation(internalLocation)
                .build();
    }

    public static List<OffenderLocationDto> getOffenders(final String prisonId, final long total) {
        Validate.notBlank(prisonId);
        Validate.isTrue(total > 0);

        final List<OffenderLocationDto> dtos = new ArrayList<>();

        for (long i = 1; i <= total; i++) {
            dtos.add(getOffender(getNextBookingId(), prisonId));
        }

        return dtos;
    }

    public static void verifyAutoAllocation(final OffenderKeyworker kwAlloc, final String prisonId, final String offenderNo, final long staffId) {
        verifyNewAllocation(kwAlloc, prisonId, offenderNo, staffId);

        assertThat(kwAlloc.getAllocationType()).isEqualTo(AllocationType.PROVISIONAL);
        assertThat(kwAlloc.getAllocationReason().getCode()).isEqualTo(AllocationReason.AUTO.getReasonCode());
    }

    public static void verifyNewAllocation(final OffenderKeyworker kwAlloc, final String prisonId, final String offenderNo, final long staffId) {
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

    public static void mockPrisonerAllocationHistory(final KeyworkerService keyworkerService,
                                                     final OffenderKeyworker... allocations) {
        final List<OffenderKeyworker> allocationHistory =
                (allocations == null) ? Collections.emptyList() : List.of(allocations);

        when(keyworkerService.getAllocationHistoryForPrisoner(anyString())).thenReturn(allocationHistory);
    }

    public static KeyworkerPool initKeyworkerPool(final KeyworkerService keyworkerService,
                                                  final PrisonSupportedService prisonSupportedService,
                                                  final Collection<KeyworkerDto> keyworkers,
                                                  final String prisonId) {
        return new KeyworkerPool(keyworkerService, prisonSupportedService, keyworkers, prisonId);
    }

    // Provides a previous Key worker allocation between specified offender and Key worker with an assigned datetime 7
    // days prior to now.
    public static OffenderKeyworker getPreviousKeyworkerAutoAllocation(final String prisonId, final String offenderNo, final long staffId) {
        return getPreviousKeyworkerAutoAllocation(prisonId, offenderNo, staffId, LocalDateTime.now().minusDays(7));
    }

    // Provides a previous Key worker allocation between specified offender and Key worker, assigned at specified datetime.
    public static OffenderKeyworker getPreviousKeyworkerAutoAllocation(final String prisonId, final String offenderNo, final long staffId, final LocalDateTime assigned) {
        Validate.notNull(assigned, "Allocation must have assigned datetime.");

        return OffenderKeyworker.builder()
                .prisonId(prisonId)
                .offenderNo(offenderNo)
                .staffId(staffId)
                .active(true)
                .assignedDateTime(assigned)
                .allocationType(AllocationType.AUTO)
                .allocationReason(allocationReason(AllocationReason.AUTO))
                .build();
    }

    public static List<OffenderKeyworker> getAllocations(final String prisonId, final Set<String> offNos) {
        Validate.notBlank(prisonId);
        Validate.notEmpty(offNos);

        final List<OffenderKeyworker> allocs = new ArrayList<>();

        offNos.forEach(offNo -> allocs.add(getPreviousKeyworkerAutoAllocation(prisonId, offNo, 1)));

        return allocs;
    }
}
