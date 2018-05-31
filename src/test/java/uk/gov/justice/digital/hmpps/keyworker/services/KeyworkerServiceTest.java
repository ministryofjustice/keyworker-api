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

    @MockBean
    private DeallocateJob deallocateJob;

    @Before
    public void setup() {
        doThrow(new PrisonNotSupportedException("Agency [MDI] is not supported by this service.")).when(prisonSupportedService).verifyPrisonMigrated(eq("MDI"));
        Prison prisonDetail = Prison.builder()
                .prisonId(TEST_AGENCY).capacityTier1(CAPACITY_TIER_1).capacityTier2(CAPACITY_TIER_2)
                .build();
        when(prisonSupportedService.getPrisonDetail(TEST_AGENCY)).thenReturn(prisonDetail);
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

        OffenderLocationDto offender1 = KeyworkerTestHelper.getOffender(61, TEST_AGENCY, offenderNo, true);
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

        OffenderLocationDto offender = KeyworkerTestHelper.getOffender(61, TEST_AGENCY, offenderNo, true);

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
    public void testGetKeyworkerDetails() {
        final long staffId = 5L;
        final int CAPACITY = 10;
        final int ALLOCATIONS = 4;
        expectKeyworkerDetailsCall(staffId, CAPACITY, ALLOCATIONS);

        KeyworkerDto keyworkerDetails = service.getKeyworkerDetails(TEST_AGENCY, staffId);

        KeyworkerTestHelper.verifyKeyworkerDto(staffId, CAPACITY, ALLOCATIONS, KeyworkerStatus.UNAVAILABLE_ANNUAL_LEAVE, keyworkerDetails);
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

    private void expectKeyworkerDetailsCall(long staffId, Integer CAPACITY, int ALLOCATIONS) {
        expectStaffRoleApiCall(staffId);

        when(keyworkerRepository.findOne(staffId)).thenReturn(Keyworker.builder()
                .staffId(staffId)
                .capacity(CAPACITY)
                .status(KeyworkerStatus.UNAVAILABLE_ANNUAL_LEAVE)
                .autoAllocationFlag(true)
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
        expectKeyworkerDetailsCall(staffId, null, ALLOCATIONS);

        KeyworkerDto keyworkerDetails = service.getKeyworkerDetails(TEST_AGENCY, staffId);

        KeyworkerTestHelper.verifyKeyworkerDto(staffId, 6, ALLOCATIONS, KeyworkerStatus.UNAVAILABLE_ANNUAL_LEAVE, keyworkerDetails);
    }

    @Test
    public void testGetKeyworkerDetailsNoKeyworkerRecord() {
        final long staffId = 5L;
        expectStaffRoleApiCall(staffId);

        when(keyworkerRepository.findOne(staffId)).thenReturn(null);
        when(repository.countByStaffIdAndPrisonIdAndActiveAndAllocationTypeIsNot(staffId, TEST_AGENCY, true, PROVISIONAL)).thenReturn(null);

        KeyworkerDto keyworkerDetails = service.getKeyworkerDetails(TEST_AGENCY, staffId);

        KeyworkerTestHelper.verifyKeyworkerDto(staffId, 6, null, KeyworkerStatus.ACTIVE, keyworkerDetails);
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
    public void testGetAllocationsForKeyworkerWithOffenderDetails() {

        OffenderLocationDto offender1 = KeyworkerTestHelper.getOffender(61, TEST_AGENCY, "1", true);
        OffenderLocationDto offender2 = KeyworkerTestHelper.getOffender(62, TEST_AGENCY, "2",true);
        OffenderLocationDto offender3 = KeyworkerTestHelper.getOffender(63, TEST_AGENCY, "3",true);

        final List<OffenderKeyworker> allocations = KeyworkerTestHelper.getAllocations(TEST_AGENCY, ImmutableSet.of("1", "2", "3"));

        // Mock allocation lookup
        when(repository.findByStaffIdAndPrisonIdAndActiveAndAllocationTypeIsNot(TEST_STAFF_ID, TEST_AGENCY, true, PROVISIONAL)).thenReturn(allocations);
        when(nomisService.getOffenderForPrison(TEST_AGENCY, offender1.getOffenderNo())).thenReturn(Optional.of(offender1));
        when(nomisService.getOffenderForPrison(TEST_AGENCY, offender2.getOffenderNo())).thenReturn(Optional.of(offender2));
        when(nomisService.getOffenderForPrison(TEST_AGENCY, offender3.getOffenderNo())).thenReturn(Optional.of(offender3));

        // Invoke service method
        List<KeyworkerAllocationDetailsDto> allocationList = service.getAllocationsForKeyworkerWithOffenderDetails(TEST_AGENCY, TEST_STAFF_ID, false);

        // Verify response
        assertThat(allocationList).hasSize(3);
        assertThat(allocationList).extracting("bookingId").isEqualTo(ImmutableList.of(61L,62L,63L));

        // Verify mocks
        verify(prisonSupportedService, times(1)).verifyPrisonMigrated(eq(TEST_AGENCY));

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
        verify(prisonSupportedService, times(1)).verifyPrisonMigrated(eq(TEST_AGENCY));

    }

    @Test
    public void testGetAllocationsForKeyworkerWithOffenderDetails_NoAssociatedEliteBookingRecord() {

        OffenderLocationDto offender1 = KeyworkerTestHelper.getOffender(61, TEST_AGENCY, "1",true);
        OffenderLocationDto offender3 = KeyworkerTestHelper.getOffender(63, TEST_AGENCY, "3",true);

        final List<OffenderKeyworker> allocations = KeyworkerTestHelper.getAllocations(TEST_AGENCY, ImmutableSet.of("1", "2", "3"));

        when(repository.findByStaffIdAndPrisonIdAndActiveAndAllocationTypeIsNot(TEST_STAFF_ID, TEST_AGENCY, true, PROVISIONAL)).thenReturn(allocations);

        when(nomisService.getOffenderForPrison(TEST_AGENCY, offender1.getOffenderNo())).thenReturn(Optional.of(offender1));
        when(nomisService.getOffenderForPrison(TEST_AGENCY, "2")).thenReturn(Optional.empty());
        when(nomisService.getOffenderForPrison(TEST_AGENCY, offender3.getOffenderNo())).thenReturn(Optional.of(offender3));

        // Invoke service method
        List<KeyworkerAllocationDetailsDto> allocationList = service.getAllocationsForKeyworkerWithOffenderDetails(TEST_AGENCY, TEST_STAFF_ID, false);

        // Verify response
        assertThat(allocationList).hasSize(2);
        assertThat(allocationList).extracting("bookingId").isEqualTo(ImmutableList.of(61L,63L));

        // Verify mocks
        verify(prisonSupportedService, times(1)).verifyPrisonMigrated(eq(TEST_AGENCY));

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

        when(nomisService.getAvailableKeyworkers(TEST_AGENCY)).thenReturn(new ResponseEntity<>(keyworkers, HttpStatus.OK));
        // Invoke service method
        List<KeyworkerDto> keyworkerList = service.getAvailableKeyworkers(TEST_AGENCY);

        // Verify response
        assertThat(keyworkerList).hasSize(6);
        assertThat(keyworkerList).extracting("numberAllocated").isEqualTo(ImmutableList.of(0,0,0,1,2,2));
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


        when(nomisService.getAvailableKeyworkers(TEST_AGENCY)).thenReturn(new ResponseEntity<>(allocations, HttpStatus.OK));
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
