package uk.gov.justice.digital.hmpps.keyworker.services;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;
import uk.gov.justice.digital.hmpps.keyworker.dto.*;
import uk.gov.justice.digital.hmpps.keyworker.exception.PrisonNotSupportedException;
import uk.gov.justice.digital.hmpps.keyworker.model.*;
import uk.gov.justice.digital.hmpps.keyworker.repository.KeyworkerRepository;
import uk.gov.justice.digital.hmpps.keyworker.repository.OffenderKeyworkerRepository;
import uk.gov.justice.digital.hmpps.keyworker.security.AuthenticationFacade;

import javax.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.*;
import static uk.gov.justice.digital.hmpps.keyworker.model.AllocationType.PROVISIONAL;
import static uk.gov.justice.digital.hmpps.keyworker.services.KeyworkerTestHelper.CAPACITY_TIER_1;
import static uk.gov.justice.digital.hmpps.keyworker.services.KeyworkerTestHelper.CAPACITY_TIER_2;

/**
 * Test class for {@link KeyworkerService}.
 */
@RunWith(SpringRunner.class)
@RestClientTest(KeyworkerService.class)
public class KeyworkerServiceTest extends AbstractServiceTest {
    private static final String TEST_AGENCY = "LEI";
    private static final String NON_MIGRATED_TEST_AGENCY = "BXI";
    private static final String TEST_USER = "VANILLA";
    private static final Long TEST_STAFF_ID = 67L;
    private static final int TEST_CAPACITY = 5;

    @Autowired
    private KeyworkerService service;

    @MockBean
    private AuthenticationFacade authenticationFacade;

    @MockBean
    private OffenderKeyworkerRepository repository;

    @MockBean
    private KeyworkerRepository keyworkerRepository;

    @MockBean
    private KeyworkerAllocationProcessor processor;

    @MockBean
    private PrisonSupportedService prisonSupportedService;

    @MockBean
    private NomisService nomisService;


    @Before
    public void setup() {
        doThrow(new PrisonNotSupportedException("Agency [MDI] is not supported by this service.")).when(prisonSupportedService).verifyPrisonMigrated(eq("MDI"));
        Prison prisonDetail = Prison.builder()
                .migrated(true)
                .autoAllocatedSupported(true)
                .supported(true)
                .prisonId(TEST_AGENCY)
                .capacityTier1(CAPACITY_TIER_1)
                .capacityTier2(CAPACITY_TIER_2)
                .build();
        when(prisonSupportedService.getPrisonDetail(TEST_AGENCY)).thenReturn(prisonDetail);
        when(prisonSupportedService.isMigrated(TEST_AGENCY)).thenReturn(Boolean.TRUE);

        Prison nonMigratedPrison = Prison.builder()
                .migrated(false)
                .autoAllocatedSupported(false)
                .supported(false)
                .prisonId(NON_MIGRATED_TEST_AGENCY)
                .build();

        when(prisonSupportedService.getPrisonDetail(NON_MIGRATED_TEST_AGENCY)).thenReturn(nonMigratedPrison);
        when(prisonSupportedService.isMigrated(NON_MIGRATED_TEST_AGENCY)).thenReturn(Boolean.FALSE);
    }
    @Test
    public void testGetUnallocatedOffendersForSupportedAgencyNoneAllocated() {
        Long count = 10L;

        List<OffenderLocationDto> testDtos = KeyworkerTestHelper.getOffenders(TEST_AGENCY, count);

        when(nomisService.getOffendersAtLocation(TEST_AGENCY, null, null)).thenReturn(testDtos);

        // Allocation processor mock setup - returning same DTOs
        when(processor.filterByUnallocated(eq(testDtos))).thenReturn(testDtos);

        // Invoke service method
        List<OffenderLocationDto> response = service.getUnallocatedOffenders(TEST_AGENCY, null, null);

        // Verify response
        assertThat(response.size()).isEqualTo(count.intValue());

        // Verify mocks
        verify(prisonSupportedService, times(1)).verifyPrisonMigrated(eq(TEST_AGENCY));
        verify(processor, times(1)).filterByUnallocated(anyListOf(OffenderLocationDto.class));

    }

    @Test
    public void testGetUnallocatedOffendersForSupportedAgencyAllAllocated() {
        Long count = 10L;

        List<OffenderLocationDto> testDtos = KeyworkerTestHelper.getOffenders(TEST_AGENCY, count);
        when(nomisService.getOffendersAtLocation(TEST_AGENCY, null, SortOrder.ASC)).thenReturn(testDtos);

        // Allocation processor mock setup - return empty list
        when(processor.filterByUnallocated(eq(testDtos))).thenReturn(Collections.emptyList());

        // Invoke service method
        List<OffenderLocationDto> response = service.getUnallocatedOffenders(TEST_AGENCY, null, null);

        // Verify response
        assertThat(response).isEmpty();

        // Verify mocks
        verify(prisonSupportedService, times(1)).verifyPrisonMigrated(eq(TEST_AGENCY));
        verify(processor, times(1)).filterByUnallocated(anyListOf(OffenderLocationDto.class));

    }

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void testAllocateValidationAgencyInvalid() {
        KeyworkerAllocationDto dto = KeyworkerAllocationDto.builder().prisonId("MDI").build();
        thrown.expectMessage("Agency [MDI] is not supported by this service.");
        service.allocate(dto);
    }

    @Test
    public void testAllocateValidationOffenderMissing() {
        KeyworkerAllocationDto dto = KeyworkerAllocationDto.builder()
                .prisonId(TEST_AGENCY)
                .offenderNo(null)
                .build();
        thrown.expectMessage("Missing prisoner number.");

        service.allocate(dto);
    }

    @Test
    public void testAllocateValidationOffenderDoesNotExist() {
        final String offenderNo = "xxx";
        KeyworkerAllocationDto dto = KeyworkerAllocationDto.builder()
                .prisonId(TEST_AGENCY)
                .offenderNo(offenderNo)
                .staffId(5L)
                .build();

        when(nomisService.getOffenderForPrison(TEST_AGENCY, offenderNo)).thenReturn(Optional.empty());

        thrown.expectMessage(String.format("Prisoner %s not found at agencyId %s", offenderNo, TEST_AGENCY));
        service.allocate(dto);
    }

    @Test
    public void testAllocateValidationStaffIdMissing() {
        final String offenderNo = "A1111AA";
        KeyworkerAllocationDto dto = KeyworkerAllocationDto.builder()
                .prisonId(TEST_AGENCY)
                .offenderNo(offenderNo)
                .build();

        thrown.expectMessage("Missing staff id");

        service.allocate(dto);
    }

    @Test
    public void testAllocateValidationStaffDoesNotExist() {
        final String offenderNo = "A1111AA";
        final long staffId = -9999L;

        KeyworkerAllocationDto dto = KeyworkerAllocationDto.builder()
                .prisonId(TEST_AGENCY)
                .offenderNo(offenderNo)
                .staffId(staffId)
                .build();

        OffenderLocationDto offender1 = KeyworkerTestHelper.getOffender(61, TEST_AGENCY, offenderNo);
        when(nomisService.getOffenderForPrison(TEST_AGENCY, offender1.getOffenderNo())).thenReturn(Optional.of(offender1));

        when(nomisService.getStaffKeyWorkerForPrison(TEST_AGENCY, staffId)).thenReturn(Optional.empty());
        when(nomisService.getBasicKeyworkerDtoForStaffId(staffId)).thenReturn(null);

        thrown.expectMessage(String.format("Keyworker %d not found at agencyId %s.", staffId, TEST_AGENCY));

        service.allocate(dto);
    }

    @Test
    public void testAllocateKeyworkerAllocationDto() {
        final String offenderNo = "A1111AA";
        final long staffId = 5;

        KeyworkerAllocationDto dto = KeyworkerAllocationDto.builder()
                .prisonId(TEST_AGENCY)
                .offenderNo(offenderNo)
                .staffId(staffId)
                .deallocationReason(DeallocationReason.RELEASED)
                .build();

        OffenderLocationDto offender = KeyworkerTestHelper.getOffender(61, TEST_AGENCY, offenderNo);

        when(nomisService.getOffenderForPrison(TEST_AGENCY, offender.getOffenderNo())).thenReturn(Optional.of(offender));

        StaffLocationRoleDto staffLocationRoleDto = StaffLocationRoleDto.builder().build();
        when(nomisService.getStaffKeyWorkerForPrison(TEST_AGENCY, staffId)).thenReturn(Optional.of(staffLocationRoleDto));
        when(nomisService.getBasicKeyworkerDtoForStaffId(staffId)).thenReturn(staffLocationRoleDto);

        final List<OffenderKeyworker> list = Arrays.asList(
                OffenderKeyworker.builder()
                        .offenderNo(offenderNo)
                        .active(true)
                        .build(),
                OffenderKeyworker.builder()
                        .offenderNo(offenderNo)
                        .active(true)
                        .build()
        );

        when(repository.findByActiveAndOffenderNo(true, offenderNo)).thenReturn(list);

        service.allocate(dto);

        assertThat(list.get(0).isActive()).isFalse();
        assertThat(list.get(0).getExpiryDateTime()).isCloseTo(LocalDateTime.now(), within(1, ChronoUnit.HOURS));
        assertThat(list.get(0).getDeallocationReason()).isEqualTo(DeallocationReason.RELEASED);
        assertThat(list.get(1).isActive()).isFalse();
        assertThat(list.get(1).getExpiryDateTime()).isCloseTo(LocalDateTime.now(), within(1, ChronoUnit.HOURS));
        assertThat(list.get(1).getDeallocationReason()).isEqualTo(DeallocationReason.RELEASED);
    }

    @Test
    public void testAllocateOffenderKeyworker() {
        final String offenderNo = "A1111AA";
        final long staffId = 5L;

        OffenderKeyworker testAlloc = getTestOffenderKeyworker(TEST_AGENCY, offenderNo, staffId);

        // Mock authenticated user
        when(authenticationFacade.getCurrentUsername()).thenReturn(TEST_USER);

        service.allocate(testAlloc);

        ArgumentCaptor<OffenderKeyworker> argCap = ArgumentCaptor.forClass(OffenderKeyworker.class);

        verify(repository, times(1)).save(argCap.capture());

        KeyworkerTestHelper.verifyNewAllocation(argCap.getValue(), TEST_AGENCY, offenderNo, staffId);
    }

    @Test
    public void testGetOffenders() {

        final LocalDateTime time1 = LocalDateTime.of(2018, Month.FEBRUARY, 26, 6, 0);
        final LocalDateTime time2 = LocalDateTime.of(2018, Month.FEBRUARY, 27, 6, 0);
        OffenderKeyworker offender1 = OffenderKeyworker.builder()
                .offenderKeyworkerId(11L)
                .offenderNo("offender1")
                .staffId(21L)
                .prisonId(TEST_AGENCY)
                .active(true)
                .assignedDateTime(time1)
                .expiryDateTime(time2)
                .userId("me")
                .build();
        OffenderKeyworker offender2 = OffenderKeyworker.builder()
                .offenderKeyworkerId(12L)
                .offenderNo("offender2")
                .active(false)
                .build();
        final List<String> testOffenderNos = Arrays.asList("offender1", "offender2");
        List<OffenderKeyworker> results = Arrays.asList(offender1, offender2);
        when(repository.findByActiveAndPrisonIdAndOffenderNoInAndAllocationTypeIsNot(true, TEST_AGENCY, testOffenderNos, PROVISIONAL)).thenReturn(results);

        final List<OffenderKeyworkerDto> offenders = service.getOffenderKeyworkerDetailList(TEST_AGENCY, testOffenderNos);

        assertThat(offenders).asList().containsExactly(OffenderKeyworkerDto.builder()
                        .offenderKeyworkerId(11L)
                        .offenderNo("offender1")
                        .staffId(21L)
                        .agencyId(TEST_AGENCY)
                        .active("Y")
                        .assigned(time1)
                        .expired(time2)
                        .userId("me")
                        .build(),
                OffenderKeyworkerDto.builder()
                        .offenderKeyworkerId(12L)
                        .offenderNo("offender2")
                        .active("N")
                        .build()
        );
    }

    @Test
    public void testGetOffendersNonMigrated() {

        final List<String> testOffenderNos = Arrays.asList("offender1", "offender2");

        final LocalDateTime time1 = LocalDateTime.of(2018, Month.FEBRUARY, 26, 6, 0);
        final LocalDateTime time2 = LocalDateTime.of(2018, Month.FEBRUARY, 27, 6, 0);

        ImmutableList<KeyworkerAllocationDetailsDto> allocatedKeyworkers = ImmutableList.of(
                KeyworkerTestHelper.getKeyworkerAllocations(21L, "offender1", NON_MIGRATED_TEST_AGENCY, time1),
                KeyworkerTestHelper.getKeyworkerAllocations(22L, "offender2", NON_MIGRATED_TEST_AGENCY, time2)
        );

        when(nomisService.getCurrentAllocationsByOffenderNos(ImmutableList.of("offender1","offender2"), NON_MIGRATED_TEST_AGENCY)).thenReturn(allocatedKeyworkers);
        final List<OffenderKeyworkerDto> offenders = service.getOffenderKeyworkerDetailList(NON_MIGRATED_TEST_AGENCY, testOffenderNos);

        assertThat(offenders).asList().containsExactly(OffenderKeyworkerDto.builder()
                        .offenderKeyworkerId(null)
                        .offenderNo("offender1")
                        .staffId(21L)
                        .agencyId(NON_MIGRATED_TEST_AGENCY)
                        .active("Y")
                        .assigned(time1)
                        .build(),
                OffenderKeyworkerDto.builder()
                        .offenderKeyworkerId(null)
                        .offenderNo("offender2")
                        .agencyId(NON_MIGRATED_TEST_AGENCY)
                        .staffId(22L)
                        .assigned(time2)
                        .active("Y")
                        .build()
        );
        verify(prisonSupportedService, times(1)).isMigrated(eq(NON_MIGRATED_TEST_AGENCY));
    }

    @Test
    public void testGetKeyworkerDetails() {
        final long staffId = 5L;
        final int CAPACITY = 10;
        final int ALLOCATIONS = 4;
        expectKeyworkerDetailsCall(staffId, CAPACITY, ALLOCATIONS, null);

        KeyworkerDto keyworkerDetails = service.getKeyworkerDetails(TEST_AGENCY, staffId);

        KeyworkerTestHelper.verifyKeyworkerDto(staffId, CAPACITY, ALLOCATIONS, KeyworkerStatus.UNAVAILABLE_ANNUAL_LEAVE, keyworkerDetails, null);
    }

    @Test
    public void testGetActiveKeyworkerForOffender() {
        final String offenderNo = "X5555XX";
        final long staffId = 5L;
        expectGetActiveKeyworkerForOffenderCall(offenderNo, staffId, true);

        Optional<BasicKeyworkerDto> keyworkerDetails = service.getCurrentKeyworkerForPrisoner(TEST_AGENCY, offenderNo);

        KeyworkerTestHelper.verifyBasicKeyworkerDto(keyworkerDetails.get(), staffId, "First", "Last");
    }

    @Test
    public void testGetActiveKeyworkerForOffenderNonYetMigrated() {
        final String offenderNo = "X5555YY";
        final long staffId = 6L;
        BasicKeyworkerDto expectedKeyworkerDto = expectGetActiveKeyworkerForOffenderCall(offenderNo, staffId, false);

        Optional<BasicKeyworkerDto> keyworkerDetails = service.getCurrentKeyworkerForPrisoner(TEST_AGENCY, offenderNo);

        KeyworkerTestHelper.verifyBasicKeyworkerDto(keyworkerDetails.get(), staffId, expectedKeyworkerDto.getFirstName(), expectedKeyworkerDto.getLastName());
    }

    private BasicKeyworkerDto expectGetActiveKeyworkerForOffenderCall(String offenderNo, long staffId, boolean agencyMigrated) {

        when(prisonSupportedService.isMigrated(isA(String.class))).thenReturn(agencyMigrated);

        if (agencyMigrated) {
            when(repository.findByOffenderNoAndActiveAndAllocationTypeIsNot(offenderNo, true, PROVISIONAL)).thenReturn(OffenderKeyworker.builder()
                    .staffId(staffId)
                    .build()
            );
            expectBasicStaffApiCall(staffId);
            return null;

        } else {
            BasicKeyworkerDto keyworkerDto = KeyworkerTestHelper.getKeyworker(staffId);
            when(nomisService.getBasicKeyworkerDtoForOffender(offenderNo)).thenReturn(keyworkerDto);
            return keyworkerDto;
        }
    }

    private void expectKeyworkerDetailsCall(long staffId, Integer CAPACITY, int ALLOCATIONS, LocalDate activeDate) {
        expectStaffRoleApiCall(staffId);

        when(keyworkerRepository.findOne(staffId)).thenReturn(Keyworker.builder()
                .staffId(staffId)
                .capacity(CAPACITY)
                .status(KeyworkerStatus.UNAVAILABLE_ANNUAL_LEAVE)
                .autoAllocationFlag(true)
                .activeDate(activeDate)
                .build()
        );
        when(repository.countByStaffIdAndPrisonIdAndActiveAndAllocationTypeIsNot(staffId, TEST_AGENCY, true, PROVISIONAL)).thenReturn(ALLOCATIONS);
    }

    private void expectStaffRoleApiCall(long staffId) {
        when(nomisService.getStaffKeyWorkerForPrison(TEST_AGENCY, staffId)).thenReturn(Optional.ofNullable(KeyworkerTestHelper.getStaffLocationRoleDto(staffId)));
    }

    private void expectBasicStaffApiCall(long staffId) {
        StaffLocationRoleDto staffLocationRoleDto = KeyworkerTestHelper.getBasicVersionOfStaffLocationRoleDto(staffId);
        when(nomisService.getBasicKeyworkerDtoForStaffId(staffId)).thenReturn(staffLocationRoleDto);
    }


    @Test
    public void testGetKeyworkerDetailsNoCapacity() {
        final long staffId = 5L;
        final int ALLOCATIONS = 4;
        final LocalDate activeDate = LocalDate.of(2018, 10, 10);
        expectKeyworkerDetailsCall(staffId, null, ALLOCATIONS, activeDate);

        KeyworkerDto keyworkerDetails = service.getKeyworkerDetails(TEST_AGENCY, staffId);

        KeyworkerTestHelper.verifyKeyworkerDto(staffId, 6, ALLOCATIONS, KeyworkerStatus.UNAVAILABLE_ANNUAL_LEAVE, keyworkerDetails, activeDate);
    }

    @Test
    public void testGetKeyworkerDetailsNoKeyworkerRecord() {
        final long staffId = 5L;
        expectStaffRoleApiCall(staffId);

        when(keyworkerRepository.findOne(staffId)).thenReturn(null);
        when(repository.countByStaffIdAndPrisonIdAndActiveAndAllocationTypeIsNot(staffId, TEST_AGENCY, true, PROVISIONAL)).thenReturn(null);

        KeyworkerDto keyworkerDetails = service.getKeyworkerDetails(TEST_AGENCY, staffId);

        KeyworkerTestHelper.verifyKeyworkerDto(staffId, 6, null, KeyworkerStatus.ACTIVE, keyworkerDetails, null);
    }

    @Test
    public void testGetKeyworkerDetails_NoEliteKeyworkerForAgency() {
        final long staffId = 5L;
        when(nomisService.getStaffKeyWorkerForPrison(TEST_AGENCY, staffId)).thenReturn(Optional.empty());
        expectBasicStaffApiCall(staffId);

        KeyworkerDto keyworkerDetails = service.getKeyworkerDetails(TEST_AGENCY, staffId);

        assertThat(keyworkerDetails.getStaffId()).isEqualTo(staffId);
        assertThat(keyworkerDetails.getFirstName()).isEqualTo("First");
        assertThat(keyworkerDetails.getLastName()).isEqualTo("Last");

        //should NOT have decorated with further information as agencyId is not present
        assertThat(keyworkerDetails.getNumberAllocated()).isEqualTo(null);
        assertThat(keyworkerDetails.getAgencyId()).isEqualTo(null);
        assertThat(keyworkerDetails.getCapacity()).isEqualTo(null);

        verify(keyworkerRepository, never()).findOne(Mockito.anyLong());
        verify(repository, never()).countByStaffIdAndPrisonIdAndActiveAndAllocationTypeIsNot(Mockito.anyLong(), Mockito.anyString(), Mockito.anyBoolean(), Mockito.anyObject());
    }

    @Test
    public void testGetKeyworkerDetails_NotMigratedAgency() {
        final long staffId = 5L;
        StaffLocationRoleDto staffLocationRoleDto = StaffLocationRoleDto.builder()
                .firstName("firstName")
                .lastName("lastName")
                .agencyId(NON_MIGRATED_TEST_AGENCY)
                .hoursPerWeek(new BigDecimal("6"))
                .staffId(staffId)
                .scheduleType("FT")
                .build();
        when(nomisService.getStaffKeyWorkerForPrison(NON_MIGRATED_TEST_AGENCY, staffId)).thenReturn(Optional.ofNullable(staffLocationRoleDto));

        ImmutableList<KeyworkerAllocationDetailsDto> allocatedKeyworkers = ImmutableList.of(
                KeyworkerTestHelper.getKeyworkerAllocations(staffId, "AA0001AA", NON_MIGRATED_TEST_AGENCY, LocalDateTime.now()),
                KeyworkerTestHelper.getKeyworkerAllocations(staffId, "AA0001AB", NON_MIGRATED_TEST_AGENCY, LocalDateTime.now()),
                KeyworkerTestHelper.getKeyworkerAllocations(staffId, "AA0001AC", NON_MIGRATED_TEST_AGENCY, LocalDateTime.now()),
                KeyworkerTestHelper.getKeyworkerAllocations(staffId, "AA0001AD", NON_MIGRATED_TEST_AGENCY, LocalDateTime.now())
        );

        when(nomisService.getCurrentAllocations(ImmutableList.of(staffId), NON_MIGRATED_TEST_AGENCY)).thenReturn(allocatedKeyworkers);

        KeyworkerDto keyworkerDetails = service.getKeyworkerDetails(NON_MIGRATED_TEST_AGENCY, staffId);

        assertThat(keyworkerDetails.getStaffId()).isEqualTo(staffId);
        assertThat(keyworkerDetails.getFirstName()).isEqualTo("firstName");
        assertThat(keyworkerDetails.getLastName()).isEqualTo("lastName");
        assertThat(keyworkerDetails.getNumberAllocated()).isEqualTo(4);
        assertThat(keyworkerDetails.getAgencyId()).isEqualTo(NON_MIGRATED_TEST_AGENCY);
        assertThat(keyworkerDetails.getCapacity()).isEqualTo(6);
    }

    @Test
    public void testGetAllocationsForKeyworkerWithOffenderDetails() {

        PrisonerDetail offender1 = KeyworkerTestHelper.getPrisonerDetail(61, TEST_AGENCY, "1", true, TEST_AGENCY+"-A-1-001");
        PrisonerDetail offender2 = KeyworkerTestHelper.getPrisonerDetail(62, "OUT", "2", false, null);
        PrisonerDetail offender3 = KeyworkerTestHelper.getPrisonerDetail(63, TEST_AGENCY, "3", true, TEST_AGENCY+"-A-2-001");

        final List<OffenderKeyworker> allocations = KeyworkerTestHelper.getAllocations(TEST_AGENCY, ImmutableSet.of("1", "2", "3"));

        // Mock allocation lookup
        when(repository.findByStaffIdAndPrisonIdAndActiveAndAllocationTypeIsNot(TEST_STAFF_ID, TEST_AGENCY, true, PROVISIONAL)).thenReturn(allocations);
        when(nomisService.getPrisonerDetail(offender1.getOffenderNo())).thenReturn(Optional.of(offender1));
        when(nomisService.getPrisonerDetail(offender2.getOffenderNo())).thenReturn(Optional.of(offender2));
        when(nomisService.getPrisonerDetail(offender3.getOffenderNo())).thenReturn(Optional.of(offender3));

        // Invoke service method
        List<KeyworkerAllocationDetailsDto> allocationList = service.getAllocationsForKeyworkerWithOffenderDetails(TEST_AGENCY, TEST_STAFF_ID, false);

        // Verify response
        assertThat(allocationList).hasSize(3);
        assertThat(allocationList).extracting("bookingId").isEqualTo(ImmutableList.of(61L,62L,63L));

        // Verify mocks
        verify(prisonSupportedService, times(1)).isMigrated(eq(TEST_AGENCY));

    }

    @Test
    public void testGetAllocationsForKeyworkerSkippingOffenderDetails() {

        ImmutableSet<String> offenderNos = ImmutableSet.of("1", "2", "3");
        final List<OffenderKeyworker> allocations = KeyworkerTestHelper.getAllocations(TEST_AGENCY, offenderNos);

        // Mock allocation lookup
        when(repository.findByStaffIdAndPrisonIdAndActiveAndAllocationTypeIsNot(TEST_STAFF_ID, TEST_AGENCY, true, PROVISIONAL)).thenReturn(allocations);

        // Invoke service method
        List<KeyworkerAllocationDetailsDto> allocationList = service.getAllocationsForKeyworkerWithOffenderDetails(TEST_AGENCY, TEST_STAFF_ID, true);

        // Verify response
        assertThat(allocationList).hasSize(3);
        assertThat(allocationList).extracting("offenderNo").isEqualTo(offenderNos.asList());

        // Verify mocks
        verify(prisonSupportedService, times(1)).isMigrated(eq(TEST_AGENCY));

    }

    @Test
    public void testGetAllocationsForKeyworkerWithOffenderDetails_NoAssociatedEliteBookingRecord() {

        PrisonerDetail offender1 = KeyworkerTestHelper.getPrisonerDetail(61, TEST_AGENCY, "1", true,TEST_AGENCY + "-A-1-001");
        PrisonerDetail offender3 = KeyworkerTestHelper.getPrisonerDetail(63, TEST_AGENCY, "3", true,TEST_AGENCY + "-A-1-002");

        final List<OffenderKeyworker> allocations = KeyworkerTestHelper.getAllocations(TEST_AGENCY, ImmutableSet.of("1", "2", "3"));

        when(repository.findByStaffIdAndPrisonIdAndActiveAndAllocationTypeIsNot(TEST_STAFF_ID, TEST_AGENCY, true, PROVISIONAL)).thenReturn(allocations);

        when(nomisService.getPrisonerDetail(offender1.getOffenderNo())).thenReturn(Optional.of(offender1));
        when(nomisService.getPrisonerDetail("2")).thenReturn(Optional.empty());
        when(nomisService.getPrisonerDetail(offender3.getOffenderNo())).thenReturn(Optional.of(offender3));

        // Invoke service method
        List<KeyworkerAllocationDetailsDto> allocationList = service.getAllocationsForKeyworkerWithOffenderDetails(TEST_AGENCY, TEST_STAFF_ID, false);

        // Verify response
        assertThat(allocationList).hasSize(2);
        assertThat(allocationList).extracting("bookingId").isEqualTo(ImmutableList.of(61L,63L));

        // Verify mocks
        verify(prisonSupportedService, times(1)).isMigrated(eq(TEST_AGENCY));
    }

    /**
     * KW search function
     */
    @Test
    public void testGetKeyworkers() {

        final Optional<String> nameFilter = Optional.of("CUser");
        final Optional<KeyworkerStatus> statusFilter = Optional.of(KeyworkerStatus.UNAVAILABLE_LONG_TERM_ABSENCE);
        final PagingAndSortingDto pagingAndSorting = PagingAndSortingDto.builder().pageLimit(50L).pageOffset(0L).build();
        final List<StaffLocationRoleDto> nomisList = Arrays.asList(
                StaffLocationRoleDto.builder()
                        .staffId(-5L)
                        .firstName("First")
                        .lastName("CUser")
                        .agencyId("LEI")
                        .position("AO")
                        .role("KW")
                        .scheduleType("FT")
                        .hoursPerWeek(BigDecimal.valueOf(11))
                        .build(),
                StaffLocationRoleDto.builder()
                        .staffId(-6L)
                        .firstName("Second")
                        .lastName("DUser")
                        .agencyId("LEI")
                        .position("AO")
                        .role("KW")
                        .scheduleType("FT")
                        .hoursPerWeek(BigDecimal.valueOf(12))
                        .build()
        );
        when(nomisService.getActiveStaffKeyWorkersForPrison(TEST_AGENCY, nameFilter, pagingAndSorting))
                .thenReturn(new ResponseEntity<>(nomisList, paginationHeaders(2, 0, 10), HttpStatus.OK));
        when(keyworkerRepository.findOne(-5L)).thenReturn(Keyworker.builder()
                .staffId(-5L)
                .status(KeyworkerStatus.UNAVAILABLE_LONG_TERM_ABSENCE)
                .capacity(5)
                .autoAllocationFlag(true)
                .activeDate(LocalDate.of(2018, Month.AUGUST, 12))
                .build()
        );
        when(keyworkerRepository.findOne(-6L)).thenReturn(Keyworker.builder()
                .staffId(-6L)
                .status(KeyworkerStatus.ACTIVE)
                .capacity(3)
                .autoAllocationFlag(true)
                .activeDate(LocalDate.of(2018, Month.AUGUST, 14))
                .build()
        );
        when(repository.countByStaffIdAndPrisonIdAndActiveAndAllocationTypeIsNot(-5L, TEST_AGENCY, true, AllocationType.PROVISIONAL))
                .thenReturn(2);
        when(repository.countByStaffIdAndPrisonIdAndActiveAndAllocationTypeIsNot(-6L, TEST_AGENCY, true, AllocationType.PROVISIONAL))
                .thenThrow(new RuntimeException("Should not be needed"));

        final Page<KeyworkerDto> keyworkerList = service.getKeyworkers(TEST_AGENCY, nameFilter, statusFilter, pagingAndSorting);

        assertThat(keyworkerList.getItems()).hasSize(1);
        final KeyworkerDto result = keyworkerList.getItems().get(0);
        assertThat(result.getStaffId()).isEqualTo(-5L);
        assertThat(result.getLastName()).isEqualTo("CUser");
        assertThat(result.getNumberAllocated()).isEqualTo(2);
        assertThat(result.getActiveDate()).isEqualTo(LocalDate.of(2018, Month.AUGUST, 12));
        assertThat(result.getAutoAllocationAllowed()).isTrue();
        assertThat(result.getStatus()).isEqualTo(KeyworkerStatus.UNAVAILABLE_LONG_TERM_ABSENCE);
    }

    @Test
    public void testGetKeyworkersNonMigrated() {

        final Optional<String> nameFilter = Optional.of("CUser");
        final Optional<KeyworkerStatus> statusFilter = Optional.of(KeyworkerStatus.ACTIVE);
        final PagingAndSortingDto pagingAndSorting = PagingAndSortingDto.builder().pageLimit(50L).pageOffset(0L).build();
        final List<StaffLocationRoleDto> nomisList = Arrays.asList(
                StaffLocationRoleDto.builder()
                        .staffId(-5L)
                        .firstName("First")
                        .lastName("CUser")
                        .agencyId("LEI")
                        .position("AO")
                        .role("KW")
                        .scheduleType("FT")
                        .hoursPerWeek(BigDecimal.valueOf(5))
                        .build(),
                StaffLocationRoleDto.builder()
                        .staffId(-6L)
                        .firstName("Second")
                        .lastName("DUser")
                        .agencyId("LEI")
                        .position("AO")
                        .role("KW")
                        .scheduleType("FT")
                        .hoursPerWeek(BigDecimal.valueOf(3))
                        .build()
        );
        when(nomisService.getActiveStaffKeyWorkersForPrison(NON_MIGRATED_TEST_AGENCY, nameFilter, pagingAndSorting))
                .thenReturn(new ResponseEntity<>(nomisList, paginationHeaders(2, 0, 10), HttpStatus.OK));

        ImmutableList<KeyworkerAllocationDetailsDto> allocatedKeyworkers = ImmutableList.of(
                KeyworkerTestHelper.getKeyworkerAllocations(-5, "AA0001AA", NON_MIGRATED_TEST_AGENCY, LocalDateTime.now()),
                KeyworkerTestHelper.getKeyworkerAllocations(-5, "AA0001AB", NON_MIGRATED_TEST_AGENCY, LocalDateTime.now())
        );

        when(nomisService.getCurrentAllocations(anyList(), eq(NON_MIGRATED_TEST_AGENCY))).thenReturn(allocatedKeyworkers);

        final Page<KeyworkerDto> keyworkerList = service.getKeyworkers(NON_MIGRATED_TEST_AGENCY, nameFilter, statusFilter, pagingAndSorting);

        assertThat(keyworkerList.getItems()).hasSize(2);
        final KeyworkerDto result1 = keyworkerList.getItems().get(0);
        assertThat(result1.getStaffId()).isEqualTo(-6L);
        assertThat(result1.getLastName()).isEqualTo("DUser");
        assertThat(result1.getNumberAllocated()).isEqualTo(0);
        assertThat(result1.getAutoAllocationAllowed()).isFalse();
        assertThat(result1.getStatus()).isEqualTo(KeyworkerStatus.ACTIVE);

        final KeyworkerDto result2 = keyworkerList.getItems().get(1);
        assertThat(result2.getStaffId()).isEqualTo(-5L);
        assertThat(result2.getLastName()).isEqualTo("CUser");
        assertThat(result2.getNumberAllocated()).isEqualTo(2);
        assertThat(result2.getAutoAllocationAllowed()).isFalse();
        assertThat(result2.getStatus()).isEqualTo(KeyworkerStatus.ACTIVE);

    }

    /**
     * KW search function
     */
    @Test
    public void testGetActiveKeyworkersWithCaseNotes() {

        final Optional<String> nameFilter = Optional.of("CUser");
        final PagingAndSortingDto pagingAndSorting = PagingAndSortingDto.builder().pageLimit(50L).pageOffset(0L).build();
        final List<StaffLocationRoleDto> nomisList = Arrays.asList(
                StaffLocationRoleDto.builder()
                        .staffId(-5L)
                        .firstName("First")
                        .lastName("CUser")
                        .agencyId("LEI")
                        .position("AO")
                        .role("KW")
                        .scheduleType("FT")
                        .hoursPerWeek(BigDecimal.valueOf(11))
                        .build(),
                StaffLocationRoleDto.builder()
                        .staffId(-6L)
                        .firstName("Second")
                        .lastName("DUser")
                        .agencyId("LEI")
                        .position("AO")
                        .role("KW")
                        .scheduleType("FT")
                        .hoursPerWeek(BigDecimal.valueOf(12))
                        .build(),
                StaffLocationRoleDto.builder()
                        .staffId(-7L)
                        .firstName("Third")
                        .lastName("DUser")
                        .agencyId("LEI")
                        .position("AO")
                        .role("KW")
                        .scheduleType("FT")
                        .hoursPerWeek(BigDecimal.valueOf(12))
                        .build()
        );
        when(nomisService.getActiveStaffKeyWorkersForPrison(TEST_AGENCY, nameFilter, pagingAndSorting))
                .thenReturn(new ResponseEntity<>(nomisList, paginationHeaders(3, 0, 10), HttpStatus.OK));
        when(keyworkerRepository.findOne(-5L)).thenReturn(Keyworker.builder()
                .staffId(-5L)
                .status(KeyworkerStatus.ACTIVE)
                .capacity(5)
                .autoAllocationFlag(true)
                .activeDate(LocalDate.of(2018, Month.AUGUST, 12))
                .build()
        );
        when(keyworkerRepository.findOne(-6L)).thenReturn(Keyworker.builder()
                .staffId(-6L)
                .status(KeyworkerStatus.ACTIVE)
                .capacity(3)
                .autoAllocationFlag(true)
                .activeDate(LocalDate.of(2018, Month.AUGUST, 14))
                .build()
        );
        when(keyworkerRepository.findOne(-7L)).thenReturn(Keyworker.builder()
                .staffId(-7L)
                .status(KeyworkerStatus.UNAVAILABLE_ANNUAL_LEAVE)
                .capacity(2)
                .autoAllocationFlag(false)
                .activeDate(LocalDate.of(2018, Month.AUGUST, 14))
                .build()
        );
        when(repository.countByStaffIdAndPrisonIdAndActiveAndAllocationTypeIsNot(-5L, TEST_AGENCY, true, AllocationType.PROVISIONAL))
                .thenReturn(2);
        when(repository.countByStaffIdAndPrisonIdAndActiveAndAllocationTypeIsNot(-6L, TEST_AGENCY, true, AllocationType.PROVISIONAL))
                .thenReturn(1);
        when(repository.countByStaffIdAndPrisonIdAndActiveAndAllocationTypeIsNot(-7L, TEST_AGENCY, true, AllocationType.PROVISIONAL))
                .thenReturn(3);

        when(nomisService.getCaseNoteUsage(eq(Arrays.asList(-5L, -6L, -7L)), eq("KA"), eq("KS"), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(Arrays.asList(
                        CaseNoteUsageDto.builder()
                                .staffId(-5L)
                                .caseNoteType("KA")
                                .caseNoteSubType("KS")
                                .latestCaseNote(LocalDate.now().minusWeeks(1))
                                .numCaseNotes(3)
                                .build(),
                        CaseNoteUsageDto.builder()
                                .staffId(-6L)
                                .caseNoteType("KA")
                                .caseNoteSubType("KS")
                                .latestCaseNote(LocalDate.now().minusWeeks(1))
                                .numCaseNotes(4)
                                .build()
                ));

        final Page<KeyworkerDto> keyworkerList = service.getKeyworkers(TEST_AGENCY, nameFilter, Optional.empty(), pagingAndSorting);

        assertThat(keyworkerList.getItems()).hasSize(3);
        final KeyworkerDto result = keyworkerList.getItems().get(0);
        assertThat(result.getStaffId()).isEqualTo(-6L);
        assertThat(result.getStatus()).isEqualTo(KeyworkerStatus.ACTIVE);
        assertThat(result.getNumberAllocated()).isEqualTo(1);
        assertThat(result.getNumKeyWorkerSessions()).isEqualTo(4);

        final KeyworkerDto result2 = keyworkerList.getItems().get(1);
        assertThat(result2.getStaffId()).isEqualTo(-5L);
        assertThat(result2.getStatus()).isEqualTo(KeyworkerStatus.ACTIVE);
        assertThat(result2.getNumberAllocated()).isEqualTo(2);
        assertThat(result2.getNumKeyWorkerSessions()).isEqualTo(3);

        final KeyworkerDto result3 = keyworkerList.getItems().get(2);
        assertThat(result3.getStaffId()).isEqualTo(-7L);
        assertThat(result3.getStatus()).isEqualTo(KeyworkerStatus.UNAVAILABLE_ANNUAL_LEAVE);
        assertThat(result3.getNumberAllocated()).isEqualTo(3);
        assertThat(result3.getNumKeyWorkerSessions()).isEqualTo(0);

    }

    @Test
    public void testCountPreviousKeyworkerSessions_WhenStatusIsNotActive() {
        final PagingAndSortingDto pagingAndSorting = PagingAndSortingDto.builder().pageLimit(50L).pageOffset(0L).build();
        final List<StaffLocationRoleDto> nomisList = Arrays.asList(
                StaffLocationRoleDto.builder()
                        .staffId(-5L)
                        .firstName("First")
                        .lastName("CUser")
                        .agencyId("LEI")
                        .position("AO")
                        .role("KW")
                        .scheduleType("FT")
                        .hoursPerWeek(BigDecimal.valueOf(11))
                        .build(),
                StaffLocationRoleDto.builder()
                        .staffId(-6L)
                        .firstName("Second")
                        .lastName("DUser")
                        .agencyId("LEI")
                        .position("AO")
                        .role("KW")
                        .scheduleType("FT")
                        .hoursPerWeek(BigDecimal.valueOf(12))
                        .build()
        );
        when(nomisService.getActiveStaffKeyWorkersForPrison(TEST_AGENCY, Optional.empty(), pagingAndSorting))
                .thenReturn(new ResponseEntity<>(nomisList, paginationHeaders(2, 0, 10), HttpStatus.OK));

        when(keyworkerRepository.findOne(-5L)).thenReturn(Keyworker.builder()
                .staffId(-5L)
                .status(KeyworkerStatus.INACTIVE)
                .capacity(5)
                .autoAllocationFlag(true)
                .activeDate(LocalDate.of(2018, Month.AUGUST, 12))
                .build()
        );
        when(keyworkerRepository.findOne(-6L)).thenReturn(Keyworker.builder()
                .staffId(-6L)
                .status(KeyworkerStatus.INACTIVE)
                .capacity(3)
                .autoAllocationFlag(true)
                .activeDate(LocalDate.of(2018, Month.AUGUST, 14))
                .build()
        );

        when(repository.countByStaffIdAndPrisonIdAndActiveAndAllocationTypeIsNot(-5L, TEST_AGENCY, true, AllocationType.PROVISIONAL))
                .thenReturn(2);
        when(repository.countByStaffIdAndPrisonIdAndActiveAndAllocationTypeIsNot(-6L, TEST_AGENCY, true, AllocationType.PROVISIONAL))
                .thenReturn(1);

        when(nomisService.getCaseNoteUsage(eq(Arrays.asList(-5L, -6L)), eq("KA"), eq("KS"), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(Arrays.asList(
                        CaseNoteUsageDto.builder()
                                .staffId(-5L)
                                .caseNoteType("KA")
                                .caseNoteSubType("KS")
                                .latestCaseNote(LocalDate.now().minusWeeks(1))
                                .numCaseNotes(3)
                                .build(),
                        CaseNoteUsageDto.builder()
                                .staffId(-6L)
                                .caseNoteType("KA")
                                .caseNoteSubType("KS")
                                .latestCaseNote(LocalDate.now().minusWeeks(1))
                                .numCaseNotes(4)
                                .build()
                ));

        final Page<KeyworkerDto> keyworkerList = service.getKeyworkers(TEST_AGENCY, Optional.empty(), Optional.empty(), pagingAndSorting);

        assertThat(keyworkerList.getItems()).hasSize(2);
        final KeyworkerDto result = keyworkerList.getItems().get(0);
        assertThat(result.getStaffId()).isEqualTo(-6L);
        assertThat(result.getStatus()).isEqualTo(KeyworkerStatus.INACTIVE);
        assertThat(result.getNumKeyWorkerSessions()).isEqualTo(4);

        final KeyworkerDto result2 = keyworkerList.getItems().get(1);
        assertThat(result2.getStaffId()).isEqualTo(-5L);
        assertThat(result2.getStatus()).isEqualTo(KeyworkerStatus.INACTIVE);
        assertThat(result2.getNumKeyWorkerSessions()).isEqualTo(3);
    }

    @Test
    public void testFullAllocationHistory() {
        final String offenderNo = "X5555XX";

        LocalDateTime now = LocalDateTime.now();
        List<OffenderKeyworker> migratedHistory = Arrays.asList(OffenderKeyworker.builder()
                .prisonId(TEST_AGENCY)
                .expiryDateTime(now.minusMonths(1))
                .assignedDateTime(now.minusMonths(2))
                .active(false)
                .allocationReason(AllocationReason.MANUAL)
                .allocationType(AllocationType.MANUAL)
                .deallocationReason(DeallocationReason.TRANSFER)
                .createUserId("staff2")
                .creationDateTime(now.minusMonths(2))
                .modifyDateTime(now.minusMonths(1))
                .modifyUserId("staff2")
                .offenderKeyworkerId(1L)
                .offenderNo(offenderNo)
                .staffId(12L)
                .userId("staff2")
                .build());

        when(repository.findByOffenderNo(offenderNo)).thenReturn(migratedHistory);

        Optional<PrisonerDetail> prisonerDetail = Optional.of(PrisonerDetail.builder()
                .currentlyInPrison("Y")
                .latestLocationId("HLI")
                .internalLocation("HLI-A-1-1")
                .latestLocation("HMP Hull")
                .latestBookingId(10000L)
                .dateOfBirth(LocalDate.now().minusYears(30))
                .firstName("offender1")
                .lastName("offenderLast1")
                .gender("M")
                .offenderNo(offenderNo)
                .build());
        when(nomisService.getPrisonerDetail(offenderNo)).thenReturn(prisonerDetail);


        List<AllocationHistoryDto> nonMigratedHistory = Arrays.asList(
                AllocationHistoryDto.builder()
                        .agencyId("LPI")
                        .active("N")
                        .assigned(now.minusMonths(3))
                        .expired(now.minusMonths(2))
                        .created(now.minusMonths(3))
                        .createdBy("staff1")
                        .modified(now.minusMonths(2))
                        .modifiedBy("staff1")
                        .offenderNo(offenderNo)
                        .staffId(11L)
                        .userId("staff1")
                        .build(),
                AllocationHistoryDto.builder()
                        .agencyId("LEI")
                        .active("N")
                        .assigned(now.minusMonths(2))
                        .expired(now.minusMonths(1))
                        .created(now.minusMonths(2))
                        .createdBy("staff2")
                        .modified(now.minusMonths(1))
                        .modifiedBy("staff2")
                        .offenderNo(offenderNo)
                        .staffId(12L)
                        .userId("staff2")
                        .build(),
                AllocationHistoryDto.builder()
                        .agencyId("HLI")
                        .active("Y")
                        .assigned(now.minusMonths(1))
                        .created(now.minusMonths(1))
                        .createdBy("staff3")
                        .modified(now.minusMonths(2))
                        .modifiedBy("staff3")
                        .offenderNo(offenderNo)
                        .staffId(13L)
                        .userId("staff3")
                        .build()
        );

        when(nomisService.getAllocationHistoryByOffenderNos(Collections.singletonList(offenderNo))).thenReturn(nonMigratedHistory);

        when(nomisService.getBasicKeyworkerDtoForStaffId(11L)).thenReturn(StaffLocationRoleDto.builder()
                .staffId(11L)
                .firstName("kwstaff1")
                .lastName("lastname-kw1")
                .agencyId("LPI")
                .agencyDescription("HMP Liverpool")
                .build());
        when(nomisService.getBasicKeyworkerDtoForStaffId(12L)).thenReturn(StaffLocationRoleDto.builder()
                .staffId(12L)
                .firstName("kwstaff2")
                .lastName("lastname-kw2")
                .agencyId("LEI")
                .agencyDescription("HMP Leeds")
                .build());
        when(nomisService.getBasicKeyworkerDtoForStaffId(13L)).thenReturn(StaffLocationRoleDto.builder()
                .staffId(13L)
                .firstName("kwstaff3")
                .lastName("lastname-kw3")
                .agencyId("HLI")
                .agencyDescription("HMP Hull")
                .build());
        when(nomisService.getStaffDetailByUserId("staff1")).thenReturn(StaffUser.builder().staffId(1L).username("staff1").firstName("staff1").lastName("lastname1").build());
        when(nomisService.getStaffDetailByUserId("staff2")).thenReturn(StaffUser.builder().staffId(2L).username("staff2").firstName("staff2").lastName("lastname2").build());
        when(nomisService.getStaffDetailByUserId("staff3")).thenReturn(StaffUser.builder().staffId(3L).username("staff3").firstName("staff3").lastName("lastname3").build());

        Optional<OffenderKeyWorkerHistory> fullAllocationHistory = service.getFullAllocationHistory(offenderNo);

        assertThat(fullAllocationHistory).isPresent();
        OffenderKeyWorkerHistory offenderKeyWorkerHistory = fullAllocationHistory.get();
        PrisonerDetail offender = offenderKeyWorkerHistory.getOffender();
        assertThat(offender.getLatestLocationId()).isEqualTo("HLI");
        assertThat(offender.getFirstName()).isEqualTo("offender1");
        assertThat(offender.getOffenderNo()).isEqualTo(offenderNo);

        List<KeyWorkerAllocation> allocationHistory = offenderKeyWorkerHistory.getAllocationHistory();
        assertThat(allocationHistory).hasSize(3);

        assertThat(allocationHistory).extracting("staffId").isEqualTo(ImmutableList.of(13L,12L,11L));
        assertThat(allocationHistory).extracting("prisonId").isEqualTo(ImmutableList.of("HLI",TEST_AGENCY,"LPI"));
        assertThat(allocationHistory).extracting("assigned").isEqualTo(ImmutableList.of(now.minusMonths(1),now.minusMonths(2),now.minusMonths(3)));
        assertThat(allocationHistory).extracting("active").isEqualTo(ImmutableList.of(true, false, false));
    }
    @Test
    public void testGetAvailableKeyworkers() {
        ImmutableList<KeyworkerDto> keyworkers = ImmutableList.of(
                KeyworkerTestHelper.getKeyworker(1, 0, 0),
                KeyworkerTestHelper.getKeyworker(2, 0, 0),
                KeyworkerTestHelper.getKeyworker(3, 0, 0),
                KeyworkerTestHelper.getKeyworker(4, 0, 0),
                KeyworkerTestHelper.getKeyworker(5, 0, 0),
                KeyworkerTestHelper.getKeyworker(6, 0, 0),
                KeyworkerTestHelper.getKeyworker(7, 0, 0)
        );

        when(keyworkerRepository.findOne(1L)).thenReturn(Keyworker.builder().staffId(1L).autoAllocationFlag(true).status(KeyworkerStatus.INACTIVE).build());
        when(keyworkerRepository.findOne(2L)).thenReturn(Keyworker.builder().staffId(2L).autoAllocationFlag(true).status(KeyworkerStatus.UNAVAILABLE_ANNUAL_LEAVE).build());
        when(keyworkerRepository.findOne(3L)).thenReturn(Keyworker.builder().staffId(3L).autoAllocationFlag(true).status(KeyworkerStatus.ACTIVE).build());
        when(keyworkerRepository.findOne(5L)).thenReturn(Keyworker.builder().staffId(3L).autoAllocationFlag(true).status(KeyworkerStatus.UNAVAILABLE_LONG_TERM_ABSENCE).build());
        when(keyworkerRepository.findOne(7L)).thenReturn(Keyworker.builder().staffId(3L).autoAllocationFlag(true).status(KeyworkerStatus.ACTIVE).build());

        when(repository.countByStaffIdAndPrisonIdAndActiveAndAllocationTypeIsNot(1L, TEST_AGENCY, true, PROVISIONAL)).thenReturn(0);
        when(repository.countByStaffIdAndPrisonIdAndActiveAndAllocationTypeIsNot(2L, TEST_AGENCY, true, PROVISIONAL)).thenReturn(2);
        when(repository.countByStaffIdAndPrisonIdAndActiveAndAllocationTypeIsNot(3L, TEST_AGENCY, true, PROVISIONAL)).thenReturn(1);
        when(repository.countByStaffIdAndPrisonIdAndActiveAndAllocationTypeIsNot(5L, TEST_AGENCY, true, PROVISIONAL)).thenReturn(0);
        when(repository.countByStaffIdAndPrisonIdAndActiveAndAllocationTypeIsNot(7L, TEST_AGENCY, true, PROVISIONAL)).thenReturn(2);

        when(nomisService.getAvailableKeyworkers(TEST_AGENCY)).thenReturn(keyworkers);
        // Invoke service method
        List<KeyworkerDto> keyworkerList = service.getAvailableKeyworkers(TEST_AGENCY, true);

        // Verify response
        assertThat(keyworkerList).hasSize(4);
        assertThat(keyworkerList).extracting("numberAllocated").isEqualTo(ImmutableList.of(0,0,1,2));
    }

    @Test
    public void testGetAvailableKeyworkersNotMigrated() {
        ImmutableList<KeyworkerDto> keyworkers = ImmutableList.of(
                KeyworkerTestHelper.getKeyworker(11, 0, 0),
                KeyworkerTestHelper.getKeyworker(12, 0, 0),
                KeyworkerTestHelper.getKeyworker(13, 0, 0),
                KeyworkerTestHelper.getKeyworker(14, 0, 0)
        );

        when(nomisService.getAvailableKeyworkers(NON_MIGRATED_TEST_AGENCY)).thenReturn(keyworkers);

        ImmutableList<KeyworkerAllocationDetailsDto> allocatedKeyworkers = ImmutableList.of(
                KeyworkerTestHelper.getKeyworkerAllocations(12, "AA0001AB", NON_MIGRATED_TEST_AGENCY, LocalDateTime.now()),
                KeyworkerTestHelper.getKeyworkerAllocations(13, "AA0001AC", NON_MIGRATED_TEST_AGENCY, LocalDateTime.now()),
                KeyworkerTestHelper.getKeyworkerAllocations(14, "AA0001AD", NON_MIGRATED_TEST_AGENCY, LocalDateTime.now()),
                KeyworkerTestHelper.getKeyworkerAllocations(14, "AA0001AE", NON_MIGRATED_TEST_AGENCY, LocalDateTime.now())
        );

        when(nomisService.getCurrentAllocations(ImmutableList.of(11L,12L,13L,14L), NON_MIGRATED_TEST_AGENCY)).thenReturn(allocatedKeyworkers);
        // Invoke service method
        List<KeyworkerDto> keyworkerList = service.getAvailableKeyworkers(NON_MIGRATED_TEST_AGENCY, true);

        // Verify response
        assertThat(keyworkerList).hasSize(4);
        assertThat(keyworkerList).extracting("numberAllocated").isEqualTo(ImmutableList.of(0,1,1,2));

        verify(prisonSupportedService, times(1)).isMigrated(eq(NON_MIGRATED_TEST_AGENCY));
    }

    @Test
    public void testGetKeyworkersAvailableforAutoAllocation() {

        ImmutableList<KeyworkerDto> allocations = ImmutableList.of(
                KeyworkerTestHelper.getKeyworker(1, 0, CAPACITY_TIER_1),
                KeyworkerTestHelper.getKeyworker(2, 0, CAPACITY_TIER_1),
                KeyworkerTestHelper.getKeyworker(3, 0, CAPACITY_TIER_1),
                KeyworkerTestHelper.getKeyworker(4, 0, CAPACITY_TIER_1));


        when(keyworkerRepository.findOne(1L)).thenReturn(Keyworker.builder().staffId(1L).autoAllocationFlag(true).build());
        when(keyworkerRepository.findOne(2L)).thenReturn(Keyworker.builder().staffId(2L).autoAllocationFlag(true).build());
        when(keyworkerRepository.findOne(3L)).thenReturn(Keyworker.builder().staffId(3L).autoAllocationFlag(true).build());
        when(keyworkerRepository.findOne(4L)).thenReturn(Keyworker.builder().staffId(4L).autoAllocationFlag(false).build());

        when(repository.countByStaffIdAndPrisonIdAndActiveAndAllocationTypeIsNot(1L, TEST_AGENCY, true, PROVISIONAL)).thenReturn(2);
        when(repository.countByStaffIdAndPrisonIdAndActiveAndAllocationTypeIsNot(2L, TEST_AGENCY, true, PROVISIONAL)).thenReturn(3);
        when(repository.countByStaffIdAndPrisonIdAndActiveAndAllocationTypeIsNot(3L, TEST_AGENCY, true, PROVISIONAL)).thenReturn(1);


        when(nomisService.getAvailableKeyworkers(TEST_AGENCY)).thenReturn(allocations);
        // Invoke service method
        List<KeyworkerDto> keyworkerList = service.getKeyworkersAvailableForAutoAllocation(TEST_AGENCY);

        // Verify response
        assertThat(keyworkerList).hasSize(3);
        //should exclude staffid 4 - autoAllocationAllowed flag is false
        assertThat(keyworkerList).extracting("numberAllocated").isEqualTo(ImmutableList.of(1,2,3));
        assertThat(keyworkerList).extracting("autoAllocationAllowed").isEqualTo(ImmutableList.of(true,true,true));

    }

    @Test
    public void testDeallocation() {
        final String offenderNo = "A1111AA";
        final long staffId = -1L;

        OffenderKeyworker testOffenderKeyworker = getTestOffenderKeyworker(TEST_AGENCY, offenderNo, staffId);
        when(repository.findByActiveAndOffenderNo(true, offenderNo)).thenReturn(Collections.singletonList(testOffenderKeyworker));

        service.deallocate(offenderNo);

        verify(repository).findByActiveAndOffenderNo(true, offenderNo);
    }

    @Test(expected = EntityNotFoundException.class)
    public void testDeallocationNoOffender() {
        final String offenderNo = "A1111AB";

        when(repository.findByActiveAndOffenderNo(true, offenderNo)).thenReturn(new ArrayList<>());

        service.deallocate(offenderNo);
    }

    @Test
    public void testThatANewKeyworkerRecordIsInserted() {
        final long staffId = 1;
        final String prisonId = "LEI";
        final int capacity = 10;
        final KeyworkerStatus status = KeyworkerStatus.ACTIVE;

        ArgumentCaptor<Keyworker> argCap = ArgumentCaptor.forClass(Keyworker.class);

        when(keyworkerRepository.findOne(staffId)).thenReturn(null);

        service.addOrUpdate(staffId,
                prisonId, KeyworkerUpdateDto.builder().capacity(capacity).status(status).build());

        verify(keyworkerRepository, times(1)).save(argCap.capture());

        assertThat(argCap.getValue().getStaffId()).isEqualTo(staffId);
        assertThat(argCap.getValue().getCapacity()).isEqualTo(capacity);
        assertThat(argCap.getValue().getStatus()).isEqualTo(status);
    }

    @Test
    public void testThatKeyworkerRecordIsUpdated() {
        final KeyworkerStatus status = KeyworkerStatus.UNAVAILABLE_ANNUAL_LEAVE;

        final Keyworker existingKeyWorker = Keyworker.builder()
                .staffId(TEST_STAFF_ID)
                .capacity(TEST_CAPACITY)
                .status(KeyworkerStatus.ACTIVE)
                .build();

        when(keyworkerRepository.findOne(TEST_STAFF_ID)).thenReturn(existingKeyWorker);

        service.addOrUpdate(TEST_STAFF_ID,
                TEST_AGENCY, KeyworkerUpdateDto.builder().capacity(TEST_CAPACITY).status(status).build());

        assertThat(existingKeyWorker.getStaffId()).isEqualTo(TEST_STAFF_ID);
        assertThat(existingKeyWorker.getCapacity()).isEqualTo(TEST_CAPACITY);
        assertThat(existingKeyWorker.getStatus()).isEqualTo(status);
    }

    @Test
    public void testThatKeyworkerRecordIsUpdated_activeStatusAutoAllocation() {

        final Keyworker existingKeyWorker = Keyworker.builder()
                .staffId(TEST_STAFF_ID)
                .capacity(TEST_CAPACITY)
                .status(KeyworkerStatus.ACTIVE)
                .autoAllocationFlag(false)
                .build();

        when(keyworkerRepository.findOne(TEST_STAFF_ID)).thenReturn(existingKeyWorker);

        service.addOrUpdate(TEST_STAFF_ID,
                TEST_AGENCY, KeyworkerUpdateDto.builder().capacity(TEST_CAPACITY).status(KeyworkerStatus.ACTIVE).build());

        assertThat(existingKeyWorker.getStatus()).isEqualTo(KeyworkerStatus.ACTIVE);
        //auto allocation flag is updated to true for active status
        assertThat(existingKeyWorker.getAutoAllocationFlag()).isEqualTo(true);
    }

    @Test
    public void testThatKeyworkerRecordIsUpdated_inactiveStatusAutoAllocation() {
        final Keyworker existingKeyWorker = Keyworker.builder()
                .staffId(TEST_STAFF_ID)
                .capacity(TEST_CAPACITY)
                .status(KeyworkerStatus.INACTIVE)
                .autoAllocationFlag(false)
                .build();

        when(keyworkerRepository.findOne(TEST_STAFF_ID)).thenReturn(existingKeyWorker);

        service.addOrUpdate(TEST_STAFF_ID,
                TEST_AGENCY, KeyworkerUpdateDto.builder().capacity(TEST_CAPACITY).status(KeyworkerStatus.INACTIVE).build());

        assertThat(existingKeyWorker.getStatus()).isEqualTo(KeyworkerStatus.INACTIVE);
        //auto allocation flag remains false for inactive status
        assertThat(existingKeyWorker.getAutoAllocationFlag()).isEqualTo(false);
    }

    @Test
    public void testkeyworkerStatusChangeBehaviour_removeAllocations() {
        final Keyworker existingKeyWorker = Keyworker.builder()
                .staffId(TEST_STAFF_ID)
                .build();

        when(keyworkerRepository.findOne(TEST_STAFF_ID)).thenReturn(existingKeyWorker);

        final List<OffenderKeyworker> allocations = KeyworkerTestHelper.getAllocations(TEST_AGENCY, ImmutableSet.of("1", "2", "3"));
        when(repository.findByStaffIdAndPrisonIdAndActive(TEST_STAFF_ID, TEST_AGENCY, true)).thenReturn(allocations);

        service.addOrUpdate(TEST_STAFF_ID,
                TEST_AGENCY, KeyworkerUpdateDto.builder().capacity(1).status(KeyworkerStatus.UNAVAILABLE_LONG_TERM_ABSENCE).behaviour(KeyworkerStatusBehaviour.REMOVE_ALLOCATIONS_NO_AUTO).build());

        verify(repository, times(1)).findByStaffIdAndPrisonIdAndActive(TEST_STAFF_ID, TEST_AGENCY, true);
    }

    @Test
    public void testkeyworkerStatusChangeBehaviour_keepAllocations() {
        final Keyworker existingKeyWorker = Keyworker.builder()
                .staffId(TEST_STAFF_ID)
                .build();

        when(keyworkerRepository.findOne(TEST_STAFF_ID)).thenReturn(existingKeyWorker);

        service.addOrUpdate(TEST_STAFF_ID,
                TEST_AGENCY, KeyworkerUpdateDto.builder().capacity(1).status(KeyworkerStatus.ACTIVE).behaviour(KeyworkerStatusBehaviour.KEEP_ALLOCATIONS).build());

        verify(repository, never()).findByStaffIdAndPrisonIdAndActive(any(), any(), anyBoolean());
    }

    private OffenderKeyworker getTestOffenderKeyworker(String prisonId, String offenderNo, long staffId) {
        return OffenderKeyworker.builder()
                .prisonId(prisonId)
                .offenderNo(offenderNo)
                .staffId(staffId)
                .allocationType(AllocationType.AUTO)
                .allocationReason(AllocationReason.AUTO)
                .build();
    }
}
