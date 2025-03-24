package uk.gov.justice.digital.hmpps.keyworker.services;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.microsoft.applicationinsights.TelemetryClient;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceDataDomain;
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceDataKey;
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceDataRepository;
import uk.gov.justice.digital.hmpps.keyworker.dto.AllocationHistoryDto;
import uk.gov.justice.digital.hmpps.keyworker.dto.CaseNoteUsageDto;
import uk.gov.justice.digital.hmpps.keyworker.dto.KeyworkerAllocationDto;
import uk.gov.justice.digital.hmpps.keyworker.dto.KeyworkerStatusBehaviour;
import uk.gov.justice.digital.hmpps.keyworker.dto.KeyworkerUpdateDto;
import uk.gov.justice.digital.hmpps.keyworker.dto.OffenderKeyWorkerHistorySummary;
import uk.gov.justice.digital.hmpps.keyworker.dto.OffenderKeyworkerDto;
import uk.gov.justice.digital.hmpps.keyworker.dto.OffenderLocationDto;
import uk.gov.justice.digital.hmpps.keyworker.dto.PagingAndSortingDto;
import uk.gov.justice.digital.hmpps.keyworker.dto.Prison;
import uk.gov.justice.digital.hmpps.keyworker.dto.PrisonerDetail;
import uk.gov.justice.digital.hmpps.keyworker.dto.SortOrder;
import uk.gov.justice.digital.hmpps.keyworker.dto.StaffLocationRoleDto;
import uk.gov.justice.digital.hmpps.keyworker.dto.StaffUser;
import uk.gov.justice.digital.hmpps.keyworker.exception.PrisonNotSupportedException;
import uk.gov.justice.digital.hmpps.keyworker.model.AllocationReason;
import uk.gov.justice.digital.hmpps.keyworker.model.AllocationType;
import uk.gov.justice.digital.hmpps.keyworker.model.DeallocationReason;
import uk.gov.justice.digital.hmpps.keyworker.model.KeyworkerStatus;
import uk.gov.justice.digital.hmpps.keyworker.model.LegacyKeyworker;
import uk.gov.justice.digital.hmpps.keyworker.model.OffenderKeyworker;
import uk.gov.justice.digital.hmpps.keyworker.repository.LegacyKeyworkerRepository;
import uk.gov.justice.digital.hmpps.keyworker.repository.OffenderKeyworkerRepository;
import uk.gov.justice.digital.hmpps.keyworker.security.AuthenticationFacade;
import uk.gov.justice.digital.hmpps.keyworker.utils.ReferenceDataHelper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.kotlin.OngoingStubbingKt.whenever;
import static uk.gov.justice.digital.hmpps.keyworker.model.AllocationType.PROVISIONAL;
import static uk.gov.justice.digital.hmpps.keyworker.services.KeyworkerService.KEYWORKER_CASENOTE_TYPE;
import static uk.gov.justice.digital.hmpps.keyworker.services.KeyworkerService.KEYWORKER_SESSION_SUB_TYPE;
import static uk.gov.justice.digital.hmpps.keyworker.services.KeyworkerTestHelper.CAPACITY_TIER_1;
import static uk.gov.justice.digital.hmpps.keyworker.services.KeyworkerTestHelper.CAPACITY_TIER_2;
import static uk.gov.justice.digital.hmpps.keyworker.utils.ReferenceDataHelper.allocationReason;
import static uk.gov.justice.digital.hmpps.keyworker.utils.ReferenceDataHelper.deallocationReason;

/**
 * Test class for {@link KeyworkerService}.
 */
@ExtendWith(SpringExtension.class)
@RestClientTest(KeyworkerService.class)
class KeyworkerServiceTest extends AbstractServiceTest {
    private static final String TEST_AGENCY = "LEI";
    private static final String NON_MIGRATED_TEST_AGENCY = "BXI";
    private static final String TEST_USER = "VANILLA";
    private static final Long TEST_STAFF_ID = 67L;
    private static final int TEST_CAPACITY = 5;

    @Autowired
    private KeyworkerService service;

    @MockitoBean
    private AuthenticationFacade authenticationFacade;

    @MockitoBean
    private OffenderKeyworkerRepository repository;

    @MockitoBean
    private LegacyKeyworkerRepository keyworkerRepository;

    @MockitoBean
    private KeyworkerAllocationProcessor processor;

    @MockitoBean
    private PrisonSupportedService prisonSupportedService;

    @MockitoBean
    private NomisService nomisService;

    @MockitoBean
    private ComplexityOfNeedService complexityOfNeedService;

    @MockitoBean
    private ReferenceDataRepository referenceDataRepository;

    @MockitoBean
    private TelemetryClient telemetryClient;

    @BeforeEach
    void setup() {
        doThrow(new PrisonNotSupportedException("Agency [MDI] is not supported by this service.")).when(prisonSupportedService).verifyPrisonMigrated(eq("MDI"));
        final var prisonDetail = Prison.builder()
            .migrated(true)
            .autoAllocatedSupported(true)
            .supported(true)
            .prisonId(TEST_AGENCY)
            .capacityTier1(CAPACITY_TIER_1)
            .capacityTier2(CAPACITY_TIER_2)
            .build();
        when(prisonSupportedService.getPrisonDetail(TEST_AGENCY)).thenReturn(prisonDetail);
        when(prisonSupportedService.isMigrated(TEST_AGENCY)).thenReturn(Boolean.TRUE);

        final var nonMigratedPrison = Prison.builder()
            .migrated(false)
            .autoAllocatedSupported(false)
            .supported(false)
            .prisonId(NON_MIGRATED_TEST_AGENCY)
            .build();

        when(prisonSupportedService.getPrisonDetail(NON_MIGRATED_TEST_AGENCY)).thenReturn(nonMigratedPrison);
        when(prisonSupportedService.isMigrated(NON_MIGRATED_TEST_AGENCY)).thenReturn(Boolean.FALSE);

        whenever(referenceDataRepository.findByKey(any())).thenAnswer(args -> ReferenceDataHelper.referenceDataOf(args.getArgument(0)));
    }

    @Test
    void testGetUnallocatedOffendersForSupportedAgencyNoneAllocated() {
        final var count = 10L;

        final var testDtos = KeyworkerTestHelper.getOffenders(TEST_AGENCY, count);

        when(complexityOfNeedService.removeOffendersWithHighComplexityOfNeed(anyString(),any()))
            .thenReturn(testDtos.stream().map(OffenderLocationDto::getOffenderNo).collect(java.util.stream.Collectors.toSet()));

        when(nomisService.getOffendersAtLocation(TEST_AGENCY, null, null, false)).thenReturn(testDtos);

        // Allocation processor mock setup - returning same DTOs
        when(processor.filterByUnallocated(eq(testDtos))).thenReturn(testDtos);

        // Invoke service method
        final var response = service.getUnallocatedOffenders(TEST_AGENCY, null, null);

        // Verify response
        assertThat(response.size()).isEqualTo((int) count);

        // Verify mocks
        verify(prisonSupportedService, times(1)).verifyPrisonMigrated(eq(TEST_AGENCY));
        verify(processor, times(1)).filterByUnallocated(anyList());

    }

    @Test
    void testGetUnallocatedOffendersForSupportedAgencyAllAllocated() {
        final var count = 10L;

        final var testDtos = KeyworkerTestHelper.getOffenders(TEST_AGENCY, count);
        when(nomisService.getOffendersAtLocation(TEST_AGENCY, null, SortOrder.ASC, false)).thenReturn(testDtos);

        // Allocation processor mock setup - return empty list
        when(processor.filterByUnallocated(eq(testDtos))).thenReturn(Collections.emptyList());

        // Invoke service method
        final var response = service.getUnallocatedOffenders(TEST_AGENCY, null, null);

        // Verify response
        assertThat(response).isEmpty();

        // Verify mocks
        verify(prisonSupportedService, times(1)).verifyPrisonMigrated(eq(TEST_AGENCY));
        verify(processor, times(1)).filterByUnallocated(anyList());

    }

    @Test
    void testComplexOffendersGetRemovedFromTheUnAllocatedList() {
        final var OFFENDER_N0_1 = "A12345";
        final var OFFENDER_NO_2 = "A12346";

        final var offenders = List.of(
            OffenderLocationDto.builder()
                .bookingId(-1L)
                .agencyId(TEST_AGENCY)
                .offenderNo(OFFENDER_N0_1)
                .lastName("Doe")
                .firstName("Bob")
                .build(),
            OffenderLocationDto.builder()
                .bookingId(-2L)
                .agencyId(TEST_AGENCY)
                .offenderNo(OFFENDER_NO_2)
                .lastName("D")
                .firstName("Doe")
                .build()
        );

        when(nomisService.getOffendersAtLocation(TEST_AGENCY, null, SortOrder.ASC, false)).thenReturn(offenders);
        when(complexityOfNeedService.removeOffendersWithHighComplexityOfNeed(anyString(), any())).thenReturn(Set.of(OFFENDER_N0_1));
        when(processor.filterByUnallocated(any())).thenReturn(offenders);

        service.getUnallocatedOffenders(TEST_AGENCY, null, SortOrder.ASC);

        verify(complexityOfNeedService).removeOffendersWithHighComplexityOfNeed(TEST_AGENCY, Set.of(OFFENDER_N0_1, OFFENDER_NO_2));
        verify(processor).filterByUnallocated(List.of(
            OffenderLocationDto.builder()
                .bookingId(-1L)
                .agencyId(TEST_AGENCY)
                .offenderNo(OFFENDER_N0_1)
                .lastName("Doe")
                .firstName("Bob")
                .build()));
    }

    @Test
    void testAllocateValidationAgencyInvalid() {
        final var dto = KeyworkerAllocationDto.builder().prisonId("MDI").build();
        assertThatThrownBy(() -> service.allocate(dto)).hasMessage("Agency [MDI] is not supported by this service.");
    }

    @Test
    void testAllocateValidationOffenderMissing() {
        final var dto = KeyworkerAllocationDto.builder()
            .prisonId(TEST_AGENCY)
            .offenderNo(null)
            .build();
        assertThatThrownBy(() -> service.allocate(dto)).hasMessage("Missing prisoner number.");
    }

    @Test
    void testAllocateValidationOffenderDoesNotExist() {
        final var offenderNo = "xxx";
        final var dto = KeyworkerAllocationDto.builder()
            .prisonId(TEST_AGENCY)
            .offenderNo(offenderNo)
            .staffId(5L)
            .build();

        when(nomisService.getOffenderForPrison(TEST_AGENCY, offenderNo)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.allocate(dto)).hasMessage(String.format("Prisoner %s not found at agencyId %s", offenderNo, TEST_AGENCY));
    }

    @Test
    void testAllocateValidationStaffIdMissing() {
        final var offenderNo = "A1111AA";
        final var dto = KeyworkerAllocationDto.builder()
            .prisonId(TEST_AGENCY)
            .offenderNo(offenderNo)
            .build();

        assertThatThrownBy(() -> service.allocate(dto)).hasMessage("Missing staff id.");
    }

    @Test
    void testAllocateValidationStaffDoesNotExist() {
        final var offenderNo = "A1111AA";
        final var staffId = -9999L;

        final var dto = KeyworkerAllocationDto.builder()
            .prisonId(TEST_AGENCY)
            .offenderNo(offenderNo)
            .staffId(staffId)
            .build();

        final var offender1 = KeyworkerTestHelper.getOffender(61, TEST_AGENCY, offenderNo);
        Mockito.when(nomisService.getOffenderForPrison(TEST_AGENCY, offender1.getOffenderNo())).thenReturn(Optional.of(offender1));

        when(nomisService.getStaffKeyWorkerForPrison(TEST_AGENCY, staffId)).thenReturn(Optional.empty());
        when(nomisService.getBasicKeyworkerDtoForStaffId(staffId)).thenReturn(null);

        assertThatThrownBy(() -> service.allocate(dto)).hasMessage(String.format("Keyworker %d not found at agencyId %s.", staffId, TEST_AGENCY));
    }

    @Test
    void testAllocateKeyworkerAllocationDto() {
        final var offenderNo = "A1111AA";
        final long staffId = 5;

        final var dto = KeyworkerAllocationDto.builder()
            .prisonId(TEST_AGENCY)
            .offenderNo(offenderNo)
            .staffId(staffId)
            .allocationReason(AllocationReason.AUTO)
            .deallocationReason(DeallocationReason.RELEASED)
            .build();

        final var offender = KeyworkerTestHelper.getOffender(61, TEST_AGENCY, offenderNo);

        when(nomisService.getOffenderForPrison(TEST_AGENCY, offender.getOffenderNo())).thenReturn(Optional.of(offender));

        final var staffLocationRoleDto = StaffLocationRoleDto.builder().build();
        when(nomisService.getStaffKeyWorkerForPrison(TEST_AGENCY, staffId)).thenReturn(Optional.of(staffLocationRoleDto));
        when(nomisService.getBasicKeyworkerDtoForStaffId(staffId)).thenReturn(staffLocationRoleDto);

        final var list = List.of(
            OffenderKeyworker.builder()
                .offenderNo(offenderNo)
                .active(true)
                .allocationReason(ReferenceDataHelper.allocationReason(AllocationReason.MANUAL))
                .build(),
            OffenderKeyworker.builder()
                .offenderNo(offenderNo)
                .active(true)
                .allocationReason(ReferenceDataHelper.allocationReason(AllocationReason.AUTO))
                .build()
        );

        when(repository.findByActiveAndOffenderNo(true, offenderNo)).thenReturn(list);

        service.allocate(dto);

        assertThat(list.get(0).isActive()).isFalse();
        assertThat(list.get(0).getExpiryDateTime()).isCloseTo(LocalDateTime.now(), within(1, ChronoUnit.HOURS));
        assertThat(list.get(0).getDeallocationReason().getCode()).isEqualTo(DeallocationReason.RELEASED.getReasonCode());
        assertThat(list.get(1).isActive()).isFalse();
        assertThat(list.get(1).getExpiryDateTime()).isCloseTo(LocalDateTime.now(), within(1, ChronoUnit.HOURS));
        assertThat(list.get(1).getDeallocationReason().getCode()).isEqualTo(DeallocationReason.RELEASED.getReasonCode());
    }

    @Test
    void testAllocateOffenderKeyworker() {
        final var offenderNo = "A1111AA";
        final var staffId = 5L;

        final var testAlloc = getTestOffenderKeyworker(offenderNo, staffId);

        // Mock authenticated user
        when(authenticationFacade.getCurrentUsername()).thenReturn(TEST_USER);

        service.allocate(testAlloc);

        final var argCap = ArgumentCaptor.forClass(OffenderKeyworker.class);

        verify(repository, times(1)).save(argCap.capture());

        KeyworkerTestHelper.verifyNewAllocation(argCap.getValue(), TEST_AGENCY, offenderNo, staffId);
    }

    @Test
    void testGetOffenders() {

        final var time1 = LocalDateTime.of(2018, Month.FEBRUARY, 26, 6, 0);
        final var time2 = LocalDateTime.of(2018, Month.FEBRUARY, 27, 6, 0);
        final var offender1 = OffenderKeyworker.builder()
            .offenderKeyworkerId(11L)
            .offenderNo("offender1")
            .staffId(21L)
            .prisonId(TEST_AGENCY)
            .active(true)
            .assignedDateTime(time1)
            .expiryDateTime(time2)
            .userId("me")
            .build();
        final var offender2 = OffenderKeyworker.builder()
            .offenderKeyworkerId(12L)
            .offenderNo("offender2")
            .active(false)
            .build();
        final var testOffenderNos = List.of("offender1", "offender2");
        final var results = List.of(offender1, offender2);
        when(repository.findByActiveAndPrisonIdAndOffenderNoInAndAllocationTypeIsNot(true, TEST_AGENCY, testOffenderNos, PROVISIONAL)).thenReturn(results);

        final var offenders = service.getOffenderKeyworkerDetailList(TEST_AGENCY, testOffenderNos);

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
    void testGetOffendersNonMigrated() {

        final var testOffenderNos = List.of("offender1", "offender2");

        final var time1 = LocalDateTime.of(2018, Month.FEBRUARY, 26, 6, 0);
        final var time2 = LocalDateTime.of(2018, Month.FEBRUARY, 27, 6, 0);

        final var allocatedKeyworkers = ImmutableList.of(
            KeyworkerTestHelper.getKeyworkerAllocations(21L, "offender1", NON_MIGRATED_TEST_AGENCY, time1),
            KeyworkerTestHelper.getKeyworkerAllocations(22L, "offender2", NON_MIGRATED_TEST_AGENCY, time2)
        );

        when(nomisService.getCurrentAllocationsByOffenderNos(ImmutableList.of("offender1", "offender2"), NON_MIGRATED_TEST_AGENCY)).thenReturn(allocatedKeyworkers);
        final var offenders = service.getOffenderKeyworkerDetailList(NON_MIGRATED_TEST_AGENCY, testOffenderNos);

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
    void testGetKeyworkerDetails() {
        final var staffId = 5L;
        final var CAPACITY = 10;
        final var ALLOCATIONS = 4;
        expectKeyworkerDetailsCall(staffId, CAPACITY, ALLOCATIONS, null);

        final var keyworkerDetails = service.getKeyworkerDetails(TEST_AGENCY, staffId);

        KeyworkerTestHelper.verifyKeyworkerDto(staffId, CAPACITY, ALLOCATIONS, KeyworkerStatus.UNAVAILABLE_ANNUAL_LEAVE, keyworkerDetails, null);
    }

    @Test
    void testGetCurrentKeyworkerForPrisoner_NoStaffDetails() {
        final var offenderNo = "X5555XX";
        final var staffId = 5L;
        when(prisonSupportedService.isMigrated(anyString())).thenReturn(true);
        when(repository.findByOffenderNoAndActiveAndAllocationTypeIsNot(offenderNo, true, PROVISIONAL)).thenReturn(List.of(OffenderKeyworker.builder()
            .staffId(staffId)
            .build()));
        final var keyworkerDetails = service.getCurrentKeyworkerForPrisoner(offenderNo);
        assertThat(keyworkerDetails).isEmpty();
    }

    @Test
    void testGetCurrentKeyworkerForPrisoner() {
        final var offenderNo = "X5555XX";
        final var staffId = 5L;
        when(prisonSupportedService.isMigrated(anyString())).thenReturn(true);
        when(repository.findByOffenderNoAndActiveAndAllocationTypeIsNot(offenderNo, true, PROVISIONAL)).thenReturn(List.of(OffenderKeyworker.builder()
            .staffId(staffId)
            .build()));
        expectBasicStaffApiCall(staffId);

        final var keyworkerDetails = service.getCurrentKeyworkerForPrisoner(offenderNo);

        KeyworkerTestHelper.verifyBasicKeyworkerDto(keyworkerDetails.orElseThrow(), staffId, "First", "Last");
    }

    @Test
    void testGetCurrentKeyworkerWithDuplicatesForPrisoner() {
        final var offenderNo = "X5555XX";
        final var staffId = 5L;
        when(prisonSupportedService.isMigrated(anyString())).thenReturn(true);
        final var now = LocalDateTime.now();
        final var keyWorkers = List.of(
            OffenderKeyworker.builder().staffId(staffId).creationDateTime(now.minusSeconds(2)).offenderKeyworkerId(1L).build(),
            OffenderKeyworker.builder().staffId(6L).creationDateTime(now).offenderKeyworkerId(3L).build(),
            OffenderKeyworker.builder().staffId(staffId).creationDateTime(now.minusSeconds(1)).offenderKeyworkerId(2L).build()
        );
        when(repository.findByOffenderNoAndActiveAndAllocationTypeIsNot(offenderNo, true, PROVISIONAL)).thenReturn(keyWorkers);
        expectBasicStaffApiCall(6L);

        final var keyworkerDetails = service.getCurrentKeyworkerForPrisoner(offenderNo);

        KeyworkerTestHelper.verifyBasicKeyworkerDto(keyworkerDetails.orElseThrow(), 6L, "First", "Last");
        assertThat(keyWorkers.get(0).getDeallocationReason().getCode()).isEqualTo(DeallocationReason.DUP.getReasonCode());
        assertThat(keyWorkers.get(1).getDeallocationReason()).isNull();
        assertThat(keyWorkers.get(2).getDeallocationReason().getCode()).isEqualTo(DeallocationReason.DUP.getReasonCode());
    }


    @Test
    void testGetCurrentKeyworkerForPrisonerNotYetMigrated() {
        final var offenderNo = "X5555YY";
        final var staffId = 6L;
        when(prisonSupportedService.isMigrated(anyString())).thenReturn(false);

        when(nomisService.getPrisonerDetail(anyString(), anyBoolean())).thenReturn(Optional.of(PrisonerDetail.builder().latestLocationId("HMP").build()));

        final var expectedKeyworkerDto = KeyworkerTestHelper.getKeyworker(staffId);
        when(nomisService.getBasicKeyworkerDtoForOffender(offenderNo)).thenReturn(expectedKeyworkerDto);

        final var keyworkerDetails = service.getCurrentKeyworkerForPrisoner(offenderNo).orElseThrow(EntityNotFoundException::new);

        KeyworkerTestHelper.verifyBasicKeyworkerDto(keyworkerDetails, staffId, expectedKeyworkerDto.getFirstName(), expectedKeyworkerDto.getLastName());
        verify(prisonSupportedService).isMigrated("HMP");
    }

    @Test
    void testGetCurrentKeyworkerForPrisoner_NotFound() {
        final var offenderNo = "X5555YY";
        when(prisonSupportedService.isMigrated(anyString())).thenReturn(false);

        final var keyworkerDetails = service.getCurrentKeyworkerForPrisoner(offenderNo);
        assertThat(keyworkerDetails).isEmpty();
        verify(nomisService, never()).getBasicKeyworkerDtoForOffender(anyString());
    }

    @Test
    void testGetCurrentKeyworkerForPrisoner_MigratedButNotInRepository() {
        final var offenderNo = "X5555YY";
        when(prisonSupportedService.isMigrated(anyString())).thenReturn(true);

        final var keyworkerDetails = service.getCurrentKeyworkerForPrisoner(offenderNo);
        assertThat(keyworkerDetails).isEmpty();
        verify(nomisService, never()).getBasicKeyworkerDtoForOffender(anyString());
    }

    private void expectKeyworkerDetailsCall(final long staffId, final Integer CAPACITY, final int ALLOCATIONS, final LocalDate activeDate) {
        expectStaffRoleApiCall(staffId);

        when(keyworkerRepository.findById(staffId)).thenReturn(Optional.of(LegacyKeyworker.builder()
            .staffId(staffId)
            .capacity(CAPACITY)
            .status(ReferenceDataMock.getKeyworkerStatuses().get(KeyworkerStatus.UNAVAILABLE_ANNUAL_LEAVE.name()))
            .autoAllocationFlag(true)
            .activeDate(activeDate)
            .build())
        );
        when(repository.countByStaffIdAndPrisonIdAndActiveAndAllocationTypeIsNot(staffId, TEST_AGENCY, true, PROVISIONAL)).thenReturn(ALLOCATIONS);
    }

    private void expectStaffRoleApiCall(final long staffId) {
        when(nomisService.getStaffKeyWorkerForPrison(TEST_AGENCY, staffId)).thenReturn(Optional.ofNullable(KeyworkerTestHelper.getStaffLocationRoleDto(staffId)));
    }

    private void expectBasicStaffApiCall(final long staffId) {
        final var staffLocationRoleDto = KeyworkerTestHelper.getBasicVersionOfStaffLocationRoleDto(staffId);
        when(nomisService.getBasicKeyworkerDtoForStaffId(staffId)).thenReturn(staffLocationRoleDto);
    }


    @Test
    void testGetKeyworkerDetailsNoCapacity() {
        final var staffId = 5L;
        final var ALLOCATIONS = 4;
        final var activeDate = LocalDate.of(2018, 10, 10);
        expectKeyworkerDetailsCall(staffId, null, ALLOCATIONS, activeDate);

        final var keyworkerDetails = service.getKeyworkerDetails(TEST_AGENCY, staffId);

        KeyworkerTestHelper.verifyKeyworkerDto(staffId, 6, ALLOCATIONS, KeyworkerStatus.UNAVAILABLE_ANNUAL_LEAVE, keyworkerDetails, activeDate);
    }

    @Test
    void testGetKeyworkerDetailsNoKeyworkerRecord() {
        final var staffId = 5L;
        expectStaffRoleApiCall(staffId);

        when(keyworkerRepository.findById(staffId)).thenReturn(Optional.empty());
        when(repository.countByStaffIdAndPrisonIdAndActiveAndAllocationTypeIsNot(staffId, TEST_AGENCY, true, PROVISIONAL)).thenReturn(null);

        final var keyworkerDetails = service.getKeyworkerDetails(TEST_AGENCY, staffId);

        KeyworkerTestHelper.verifyKeyworkerDto(staffId, 6, null, KeyworkerStatus.ACTIVE, keyworkerDetails, null);
    }

    @Test
    void testGetKeyworkerDetails_NoPrisonKeyworkerForAgency() {
        final var staffId = 5L;
        when(nomisService.getStaffKeyWorkerForPrison(TEST_AGENCY, staffId)).thenReturn(Optional.empty());
        expectBasicStaffApiCall(staffId);

        final var keyworkerDetails = service.getKeyworkerDetails(TEST_AGENCY, staffId);

        assertThat(keyworkerDetails.getStaffId()).isEqualTo(staffId);
        assertThat(keyworkerDetails.getFirstName()).isEqualTo("First");
        assertThat(keyworkerDetails.getLastName()).isEqualTo("Last");

        //should NOT have decorated with further information as agencyId is not present
        assertThat(keyworkerDetails.getNumberAllocated()).isEqualTo(null);
        assertThat(keyworkerDetails.getAgencyId()).isEqualTo(null);
        assertThat(keyworkerDetails.getCapacity()).isEqualTo(null);

        verify(keyworkerRepository, never()).findById(Mockito.anyLong());
        verify(repository, never()).countByStaffIdAndPrisonIdAndActiveAndAllocationTypeIsNot(Mockito.anyLong(), Mockito.anyString(), Mockito.anyBoolean(), ArgumentMatchers.any());
    }

    @Test
    void testGetKeyworkerDetails_NotMigratedAgency() {
        final var staffId = 5L;
        final var staffLocationRoleDto = StaffLocationRoleDto.builder()
            .firstName("firstName")
            .lastName("lastName")
            .agencyId(NON_MIGRATED_TEST_AGENCY)
            .hoursPerWeek(new BigDecimal("6"))
            .staffId(staffId)
            .scheduleType("FT")
            .build();
        when(nomisService.getStaffKeyWorkerForPrison(NON_MIGRATED_TEST_AGENCY, staffId)).thenReturn(Optional.ofNullable(staffLocationRoleDto));

        final var allocatedKeyworkers = ImmutableList.of(
            KeyworkerTestHelper.getKeyworkerAllocations(staffId, "AA0001AA", NON_MIGRATED_TEST_AGENCY, LocalDateTime.now()),
            KeyworkerTestHelper.getKeyworkerAllocations(staffId, "AA0001AB", NON_MIGRATED_TEST_AGENCY, LocalDateTime.now()),
            KeyworkerTestHelper.getKeyworkerAllocations(staffId, "AA0001AC", NON_MIGRATED_TEST_AGENCY, LocalDateTime.now()),
            KeyworkerTestHelper.getKeyworkerAllocations(staffId, "AA0001AD", NON_MIGRATED_TEST_AGENCY, LocalDateTime.now())
        );

        when(nomisService.getCurrentAllocations(ImmutableList.of(staffId), NON_MIGRATED_TEST_AGENCY)).thenReturn(allocatedKeyworkers);

        final var keyworkerDetails = service.getKeyworkerDetails(NON_MIGRATED_TEST_AGENCY, staffId);

        assertThat(keyworkerDetails.getStaffId()).isEqualTo(staffId);
        assertThat(keyworkerDetails.getFirstName()).isEqualTo("firstName");
        assertThat(keyworkerDetails.getLastName()).isEqualTo("lastName");
        assertThat(keyworkerDetails.getNumberAllocated()).isEqualTo(4);
        assertThat(keyworkerDetails.getAgencyId()).isEqualTo(NON_MIGRATED_TEST_AGENCY);
        assertThat(keyworkerDetails.getCapacity()).isEqualTo(6);
    }

    @Test
    void testGetAllocationsForKeyworkerWithOffenderDetails() {

        final var offender1 = KeyworkerTestHelper.getPrisonerDetail(61, TEST_AGENCY, "1", true, TEST_AGENCY + "-A-1-001");
        final var offender2 = KeyworkerTestHelper.getPrisonerDetail(62, "OUT", "2", false, null);
        final var offender3 = KeyworkerTestHelper.getPrisonerDetail(63, TEST_AGENCY, "3", true, TEST_AGENCY + "-A-2-001");

        final var allocations = KeyworkerTestHelper.getAllocations(TEST_AGENCY, ImmutableSet.of("1", "2", "3"));

        // Mock allocation lookup
        when(repository.findByStaffIdAndPrisonIdAndActiveAndAllocationTypeIsNot(TEST_STAFF_ID, TEST_AGENCY, true, PROVISIONAL)).thenReturn(allocations);
        when(nomisService.getPrisonerDetails(List.of(offender1.getOffenderNo(), offender2.getOffenderNo(), offender3.getOffenderNo()), true)).thenReturn(List.of(offender1, offender2, offender3));

        // Invoke service method
        final var allocationList = service.getAllocationsForKeyworkerWithOffenderDetails(TEST_AGENCY, TEST_STAFF_ID, false);

        // Verify response
        assertThat(allocationList).hasSize(3);
        assertThat(allocationList).extracting("bookingId").isEqualTo(ImmutableList.of(61L, 62L, 63L));

        // Verify mocks
        verify(prisonSupportedService, times(1)).isMigrated(eq(TEST_AGENCY));

    }

    @Test
    void testGetAllocationsForKeyworkerSkippingOffenderDetails() {

        final var offenderNos = ImmutableSet.of("1", "2", "3");
        final var allocations = KeyworkerTestHelper.getAllocations(TEST_AGENCY, offenderNos);

        // Mock allocation lookup
        when(repository.findByStaffIdAndPrisonIdAndActiveAndAllocationTypeIsNot(TEST_STAFF_ID, TEST_AGENCY, true, PROVISIONAL)).thenReturn(allocations);

        // Invoke service method
        final var allocationList = service.getAllocationsForKeyworkerWithOffenderDetails(TEST_AGENCY, TEST_STAFF_ID, true);

        // Verify response
        assertThat(allocationList).hasSize(3);
        assertThat(allocationList).extracting("offenderNo").isEqualTo(offenderNos.asList());

        // Verify mocks
        verify(prisonSupportedService, times(1)).isMigrated(eq(TEST_AGENCY));

    }

    @Test
    void testGetAllocationsForKeyworkerWithOffenderDetails_NoAssociatedprisonBookingRecord() {

        final var offender1 = KeyworkerTestHelper.getPrisonerDetail(61, TEST_AGENCY, "1", true, TEST_AGENCY + "-A-1-001");
        final var offender3 = KeyworkerTestHelper.getPrisonerDetail(63, TEST_AGENCY, "3", true, TEST_AGENCY + "-A-1-002");

        final var allocations = KeyworkerTestHelper.getAllocations(TEST_AGENCY, ImmutableSet.of("1", "2", "3"));

        when(repository.findByStaffIdAndPrisonIdAndActiveAndAllocationTypeIsNot(TEST_STAFF_ID, TEST_AGENCY, true, PROVISIONAL)).thenReturn(allocations);

        when(nomisService.getPrisonerDetails(List.of(offender1.getOffenderNo(), "2", offender3.getOffenderNo()), true)).thenReturn(List.of(offender1, offender3));

        // Invoke service method
        final var allocationList = service.getAllocationsForKeyworkerWithOffenderDetails(TEST_AGENCY, TEST_STAFF_ID, false);

        // Verify response
        assertThat(allocationList).hasSize(2);
        assertThat(allocationList).extracting("bookingId").isEqualTo(ImmutableList.of(61L, 63L));

        // Verify mocks
        verify(prisonSupportedService, times(1)).isMigrated(eq(TEST_AGENCY));
    }

    /**
     * KW search function
     */
    @Test
    void testGetKeyworkers() {

        final var nameFilter = Optional.of("CUser");
        final var statusFilter = Optional.of(KeyworkerStatus.UNAVAILABLE_LONG_TERM_ABSENCE);
        final var pagingAndSorting = PagingAndSortingDto.builder().pageLimit(50L).pageOffset(0L).build();
        final var nomisList = List.of(
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
        when(nomisService.getActiveStaffKeyWorkersForPrison(TEST_AGENCY, nameFilter, pagingAndSorting, false))
            .thenReturn(new ResponseEntity<>(nomisList, paginationHeaders(2, 0, 10), HttpStatus.OK));
        when(keyworkerRepository.findById(-5L)).thenReturn(Optional.of(LegacyKeyworker.builder()
            .staffId(-5L)
            .status(ReferenceDataMock.getKeyworkerStatuses().get(KeyworkerStatus.UNAVAILABLE_LONG_TERM_ABSENCE.name()))
            .capacity(5)
            .autoAllocationFlag(true)
            .activeDate(LocalDate.of(2018, Month.AUGUST, 12))
            .build())
        );
        when(keyworkerRepository.findById(-6L)).thenReturn(Optional.of(LegacyKeyworker.builder()
            .staffId(-6L)
            .status(ReferenceDataMock.getKeyworkerStatuses().get(KeyworkerStatus.ACTIVE.name()))
            .capacity(3)
            .autoAllocationFlag(true)
            .activeDate(LocalDate.of(2018, Month.AUGUST, 14))
            .build())
        );
        when(repository.countByStaffIdAndPrisonIdAndActiveAndAllocationTypeIsNot(-5L, TEST_AGENCY, true, AllocationType.PROVISIONAL))
            .thenReturn(2);
        when(repository.countByStaffIdAndPrisonIdAndActiveAndAllocationTypeIsNot(-6L, TEST_AGENCY, true, AllocationType.PROVISIONAL))
            .thenThrow(new RuntimeException("Should not be needed"));

        final var keyworkerList = service.getKeyworkers(TEST_AGENCY, nameFilter, statusFilter, pagingAndSorting);

        assertThat(keyworkerList.getItems()).hasSize(1);
        final var result = keyworkerList.getItems().get(0);
        assertThat(result.getStaffId()).isEqualTo(-5L);
        assertThat(result.getLastName()).isEqualTo("CUser");
        assertThat(result.getNumberAllocated()).isEqualTo(2);
        assertThat(result.getActiveDate()).isEqualTo(LocalDate.of(2018, Month.AUGUST, 12));
        assertThat(result.getAutoAllocationAllowed()).isTrue();
        assertThat(result.getStatus()).isEqualTo(KeyworkerStatus.UNAVAILABLE_LONG_TERM_ABSENCE);
    }

    @Test
    void testGetKeyworkersNonMigrated() {

        final var nameFilter = Optional.of("CUser");
        final var statusFilter = Optional.of(KeyworkerStatus.ACTIVE);
        final var pagingAndSorting = PagingAndSortingDto.builder().pageLimit(50L).pageOffset(0L).build();
        final var nomisList = List.of(
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
        when(nomisService.getActiveStaffKeyWorkersForPrison(NON_MIGRATED_TEST_AGENCY, nameFilter, pagingAndSorting, false))
            .thenReturn(new ResponseEntity<>(nomisList, paginationHeaders(2, 0, 10), HttpStatus.OK));

        final var allocatedKeyworkers = ImmutableList.of(
            KeyworkerTestHelper.getKeyworkerAllocations(-5, "AA0001AA", NON_MIGRATED_TEST_AGENCY, LocalDateTime.now()),
            KeyworkerTestHelper.getKeyworkerAllocations(-5, "AA0001AB", NON_MIGRATED_TEST_AGENCY, LocalDateTime.now())
        );

        when(nomisService.getCurrentAllocations(anyList(), eq(NON_MIGRATED_TEST_AGENCY))).thenReturn(allocatedKeyworkers);

        final var keyworkerList = service.getKeyworkers(NON_MIGRATED_TEST_AGENCY, nameFilter, statusFilter, pagingAndSorting);

        assertThat(keyworkerList.getItems()).hasSize(2);
        final var result1 = keyworkerList.getItems().get(0);
        assertThat(result1.getStaffId()).isEqualTo(-6L);
        assertThat(result1.getLastName()).isEqualTo("DUser");
        assertThat(result1.getNumberAllocated()).isEqualTo(0);
        assertThat(result1.getAutoAllocationAllowed()).isFalse();
        assertThat(result1.getStatus()).isEqualTo(KeyworkerStatus.ACTIVE);

        final var result2 = keyworkerList.getItems().get(1);
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
    void testGetActiveKeyworkersWithCaseNotes() {

        final var nameFilter = Optional.of("CUser");
        final var pagingAndSorting = PagingAndSortingDto.builder().pageLimit(50L).pageOffset(0L).build();
        final var nomisList = List.of(
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
        when(nomisService.getActiveStaffKeyWorkersForPrison(TEST_AGENCY, nameFilter, pagingAndSorting, false))
            .thenReturn(new ResponseEntity<>(nomisList, paginationHeaders(3, 0, 10), HttpStatus.OK));
        when(keyworkerRepository.findById(-5L)).thenReturn(Optional.of(LegacyKeyworker.builder()
            .staffId(-5L)
            .status(ReferenceDataMock.getKeyworkerStatuses().get(KeyworkerStatus.ACTIVE.name()))
            .capacity(5)
            .autoAllocationFlag(true)
            .activeDate(LocalDate.of(2018, Month.AUGUST, 12))
            .build())
        );
        when(keyworkerRepository.findById(-6L)).thenReturn(Optional.of(LegacyKeyworker.builder()
            .staffId(-6L)
            .status(ReferenceDataMock.getKeyworkerStatuses().get(KeyworkerStatus.ACTIVE.name()))
            .capacity(3)
            .autoAllocationFlag(true)
            .activeDate(LocalDate.of(2018, Month.AUGUST, 14))
            .build())
        );
        when(keyworkerRepository.findById(-7L)).thenReturn(Optional.of(LegacyKeyworker.builder()
            .staffId(-7L)
            .status(ReferenceDataMock.getKeyworkerStatuses().get(KeyworkerStatus.UNAVAILABLE_ANNUAL_LEAVE.name()))
            .capacity(2)
            .autoAllocationFlag(false)
            .activeDate(LocalDate.of(2018, Month.AUGUST, 14))
            .build())
        );
        when(repository.countByStaffIdAndPrisonIdAndActiveAndAllocationTypeIsNot(-5L, TEST_AGENCY, true, AllocationType.PROVISIONAL))
            .thenReturn(2);
        when(repository.countByStaffIdAndPrisonIdAndActiveAndAllocationTypeIsNot(-6L, TEST_AGENCY, true, AllocationType.PROVISIONAL))
            .thenReturn(1);
        when(repository.countByStaffIdAndPrisonIdAndActiveAndAllocationTypeIsNot(-7L, TEST_AGENCY, true, AllocationType.PROVISIONAL))
            .thenReturn(3);

        when(nomisService.getCaseNoteUsage(eq("LEI"), eq(List.of(-5L, -6L, -7L)), eq(KEYWORKER_CASENOTE_TYPE), eq(KEYWORKER_SESSION_SUB_TYPE), any(), any()))
            .thenReturn(List.of(
                CaseNoteUsageDto.builder()
                    .staffId(-5L)
                    .caseNoteType(KEYWORKER_CASENOTE_TYPE)
                    .caseNoteSubType(KEYWORKER_SESSION_SUB_TYPE)
                    .latestCaseNote(LocalDate.now().minusWeeks(1))
                    .numCaseNotes(3)
                    .build(),
                CaseNoteUsageDto.builder()
                    .staffId(-6L)
                    .caseNoteType(KEYWORKER_CASENOTE_TYPE)
                    .caseNoteSubType(KEYWORKER_SESSION_SUB_TYPE)
                    .latestCaseNote(LocalDate.now().minusWeeks(1))
                    .numCaseNotes(4)
                    .build()
            ));

        final var keyworkerList = service.getKeyworkers(TEST_AGENCY, nameFilter, Optional.empty(), pagingAndSorting);

        assertThat(keyworkerList.getItems()).hasSize(3);
        final var result = keyworkerList.getItems().get(0);
        assertThat(result.getStaffId()).isEqualTo(-6L);
        assertThat(result.getStatus()).isEqualTo(KeyworkerStatus.ACTIVE);
        assertThat(result.getNumberAllocated()).isEqualTo(1);
        assertThat(result.getNumKeyWorkerSessions()).isEqualTo(4);

        final var result2 = keyworkerList.getItems().get(1);
        assertThat(result2.getStaffId()).isEqualTo(-5L);
        assertThat(result2.getStatus()).isEqualTo(KeyworkerStatus.ACTIVE);
        assertThat(result2.getNumberAllocated()).isEqualTo(2);
        assertThat(result2.getNumKeyWorkerSessions()).isEqualTo(3);

        final var result3 = keyworkerList.getItems().get(2);
        assertThat(result3.getStaffId()).isEqualTo(-7L);
        assertThat(result3.getStatus()).isEqualTo(KeyworkerStatus.UNAVAILABLE_ANNUAL_LEAVE);
        assertThat(result3.getNumberAllocated()).isEqualTo(3);
        assertThat(result3.getNumKeyWorkerSessions()).isEqualTo(0);

    }

    @Test
    void testCountPreviousKeyworkerSessions_WhenStatusIsNotActive() {
        final var pagingAndSorting = PagingAndSortingDto.builder().pageLimit(50L).pageOffset(0L).build();
        final var nomisList = List.of(
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
        when(nomisService.getActiveStaffKeyWorkersForPrison(TEST_AGENCY, Optional.empty(), pagingAndSorting, false))
            .thenReturn(new ResponseEntity<>(nomisList, paginationHeaders(2, 0, 10), HttpStatus.OK));

        when(keyworkerRepository.findById(-5L)).thenReturn(Optional.of(LegacyKeyworker.builder()
            .staffId(-5L)
            .status(ReferenceDataMock.getKeyworkerStatuses().get(KeyworkerStatus.INACTIVE.name()))
            .capacity(5)
            .autoAllocationFlag(true)
            .activeDate(LocalDate.of(2018, Month.AUGUST, 12))
            .build())
        );
        when(keyworkerRepository.findById(-6L)).thenReturn(Optional.of(LegacyKeyworker.builder()
            .staffId(-6L)
            .status(ReferenceDataMock.getKeyworkerStatuses().get(KeyworkerStatus.INACTIVE.name()))
            .capacity(3)
            .autoAllocationFlag(true)
            .activeDate(LocalDate.of(2018, Month.AUGUST, 14))
            .build())
        );

        when(repository.countByStaffIdAndPrisonIdAndActiveAndAllocationTypeIsNot(-5L, TEST_AGENCY, true, AllocationType.PROVISIONAL))
            .thenReturn(2);
        when(repository.countByStaffIdAndPrisonIdAndActiveAndAllocationTypeIsNot(-6L, TEST_AGENCY, true, AllocationType.PROVISIONAL))
            .thenReturn(1);

        when(nomisService.getCaseNoteUsage(eq("LEI"), eq(List.of(-5L, -6L)), eq(KEYWORKER_CASENOTE_TYPE), eq(KEYWORKER_SESSION_SUB_TYPE), any(), any()))
            .thenReturn(Arrays.asList(
                CaseNoteUsageDto.builder()
                    .staffId(-5L)
                    .caseNoteType(KEYWORKER_CASENOTE_TYPE)
                    .caseNoteSubType(KEYWORKER_SESSION_SUB_TYPE)
                    .latestCaseNote(LocalDate.now().minusWeeks(1))
                    .numCaseNotes(3)
                    .build(),
                CaseNoteUsageDto.builder()
                    .staffId(-6L)
                    .caseNoteType(KEYWORKER_CASENOTE_TYPE)
                    .caseNoteSubType(KEYWORKER_SESSION_SUB_TYPE)
                    .latestCaseNote(LocalDate.now().minusWeeks(1))
                    .numCaseNotes(4)
                    .build()
            ));

        final var keyworkerList = service.getKeyworkers(TEST_AGENCY, Optional.empty(), Optional.empty(), pagingAndSorting);

        assertThat(keyworkerList.getItems()).hasSize(2);
        final var result = keyworkerList.getItems().get(0);
        assertThat(result.getStaffId()).isEqualTo(-6L);
        assertThat(result.getStatus()).isEqualTo(KeyworkerStatus.INACTIVE);
        assertThat(result.getNumKeyWorkerSessions()).isEqualTo(4);

        final var result2 = keyworkerList.getItems().get(1);
        assertThat(result2.getStaffId()).isEqualTo(-5L);
        assertThat(result2.getStatus()).isEqualTo(KeyworkerStatus.INACTIVE);
        assertThat(result2.getNumKeyWorkerSessions()).isEqualTo(3);
    }

    @Test
    void testFullAllocationHistory() {
        final var offenderNo = "X5555XX";

        final var now = LocalDateTime.now();
        final var migratedHistory = List.of(OffenderKeyworker.builder()
            .prisonId(TEST_AGENCY)
            .expiryDateTime(now.minusMonths(1))
            .assignedDateTime(now.minusMonths(2))
            .active(false)
            .allocationReason(allocationReason(AllocationReason.MANUAL))
            .allocationType(AllocationType.MANUAL)
            .deallocationReason(deallocationReason(DeallocationReason.TRANSFER))
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

        final var prisonerDetail = Optional.of(PrisonerDetail.builder()
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
        when(nomisService.getPrisonerDetail(offenderNo, false)).thenReturn(prisonerDetail);


        final var nonMigratedHistory = List.of(
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

        final var fullAllocationHistory = service.getFullAllocationHistory(offenderNo);

        assertThat(fullAllocationHistory).isPresent();
        final var offenderKeyWorkerHistory = fullAllocationHistory.get();
        final var offender = offenderKeyWorkerHistory.getOffender();
        assertThat(offender.getLatestLocationId()).isEqualTo("HLI");
        assertThat(offender.getFirstName()).isEqualTo("offender1");
        assertThat(offender.getOffenderNo()).isEqualTo(offenderNo);

        final var allocationHistory = offenderKeyWorkerHistory.getAllocationHistory();
        assertThat(allocationHistory).hasSize(3);

        assertThat(allocationHistory).extracting("staffId").isEqualTo(ImmutableList.of(13L, 12L, 11L));
        assertThat(allocationHistory).extracting("prisonId").isEqualTo(ImmutableList.of("HLI", TEST_AGENCY, "LPI"));
        assertThat(allocationHistory).extracting("assigned").isEqualTo(ImmutableList.of(now.minusMonths(1), now.minusMonths(2), now.minusMonths(3)));
        assertThat(allocationHistory).extracting("active").isEqualTo(ImmutableList.of(true, false, false));
    }

    @Test
    void testAllocationHistorySummary_ReturnsRequestedOffenderData() {
        final var offenderNo1 = "X1111XX";
        final var offenderNo2 = "X2222XX";
        final var offenderNo3 = "X3333XX";

        final var now = LocalDateTime.now();
        final var migratedHistory = List.of(
            getTestOffenderKeyworker(offenderNo1, 1L),
            getTestOffenderKeyworker(offenderNo1, 2L),
            getTestOffenderKeyworker(offenderNo2, 2L)
        );

        when(repository.findByOffenderNoIn(any())).thenReturn(migratedHistory);

        final var nonMigratedHistory = List.of(
            AllocationHistoryDto.builder()
                .agencyId("LPI")
                .active("N")
                .assigned(now.minusMonths(3))
                .expired(now.minusMonths(2))
                .created(now.minusMonths(3))
                .createdBy("staff1")
                .modified(now.minusMonths(2))
                .modifiedBy("staff1")
                .offenderNo(offenderNo1)
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
                .offenderNo(offenderNo3)
                .staffId(12L)
                .userId("staff2")
                .build(),
            AllocationHistoryDto.builder()
                .agencyId("LEI")
                .active("N")
                .assigned(now.minusMonths(1))
                .created(now.minusMonths(1))
                .createdBy("staff2")
                .modified(now.minusMonths(1))
                .modifiedBy("staff2")
                .offenderNo(offenderNo3)
                .staffId(14L)
                .userId("staff2")
                .build()
        );

        when(nomisService.getAllocationHistoryByOffenderNos(any())).thenReturn(nonMigratedHistory);

        final var summaries = service.getAllocationHistorySummary(List.of(offenderNo1, offenderNo2, offenderNo3));

        assertThat(summaries).containsExactlyInAnyOrder(
            OffenderKeyWorkerHistorySummary.builder()
                .offenderNo(offenderNo1)
                .hasHistory(true)
                .build(),
            OffenderKeyWorkerHistorySummary.builder()
                .offenderNo(offenderNo2)
                .hasHistory(true)
                .build(),
            OffenderKeyWorkerHistorySummary.builder()
                .offenderNo(offenderNo3)
                .hasHistory(true)
                .build());
    }

    @Test
    void testAllocationHistorySummary_DoesNotReturnProvisionalAllocations() {
        final var offenderNo = "X5555XX";

        when(repository.findByOffenderNoIn(any())).thenReturn(List.of(getTestOffenderKeyworker(offenderNo, 1L).toBuilder()
            .allocationType(PROVISIONAL)
            .build()));
        when(nomisService.getAllocationHistoryByOffenderNos(any())).thenReturn(List.of());

        final var summaries = service.getAllocationHistorySummary(List.of(offenderNo));

        assertThat(summaries).containsExactlyInAnyOrder(
            OffenderKeyWorkerHistorySummary.builder()
                .offenderNo(offenderNo)
                .hasHistory(false)
                .build());
    }

    @Test
    void testAllocationHistorySummary_ReturnsRequestedOffendersIfNoData() {
        final var offenderNo = "X5555XX";

        when(repository.findByOffenderNoIn(any())).thenReturn(List.of());
        when(nomisService.getAllocationHistoryByOffenderNos(any())).thenReturn(List.of());

        final var summaries = service.getAllocationHistorySummary(List.of(offenderNo));

        assertThat(summaries).containsExactlyInAnyOrder(
            OffenderKeyWorkerHistorySummary.builder()
                .offenderNo(offenderNo)
                .hasHistory(false)
                .build());
    }

    @Test
    void testAllocationHistorySummary_CallsKeyworkerDBAndNomis() {
        final var offenderNo = "X5555XX";

        when(repository.findByOffenderNoIn(any())).thenReturn(List.of());
        when(nomisService.getAllocationHistoryByOffenderNos(any())).thenReturn(List.of());

        service.getAllocationHistorySummary(List.of(offenderNo));

        verify(repository, times(1)).findByOffenderNoIn(eq(List.of(offenderNo)));
        verify(nomisService, times(1)).getAllocationHistoryByOffenderNos(List.of(offenderNo));
    }

    @Test
    void testGetAvailableKeyworkers() {
        final var keyworkers = ImmutableList.of(
            KeyworkerTestHelper.getKeyworker(1, 0, 0),
            KeyworkerTestHelper.getKeyworker(2, 0, 0),
            KeyworkerTestHelper.getKeyworker(3, 0, 0),
            KeyworkerTestHelper.getKeyworker(4, 0, 0),
            KeyworkerTestHelper.getKeyworker(5, 0, 0),
            KeyworkerTestHelper.getKeyworker(6, 0, 0),
            KeyworkerTestHelper.getKeyworker(7, 0, 0)
        );

        when(keyworkerRepository.findById(1L)).thenReturn(Optional.of(LegacyKeyworker.builder().staffId(1L).autoAllocationFlag(true)
            .status(ReferenceDataMock.getKeyworkerStatuses().get(KeyworkerStatus.INACTIVE.name()))
            .build()));
        when(keyworkerRepository.findById(2L)).thenReturn(Optional.of(LegacyKeyworker.builder().staffId(2L).autoAllocationFlag(true)
            .status(ReferenceDataMock.getKeyworkerStatuses().get(KeyworkerStatus.UNAVAILABLE_ANNUAL_LEAVE.name()))
            .build()));
        when(keyworkerRepository.findById(3L)).thenReturn(Optional.of(LegacyKeyworker.builder().staffId(3L).autoAllocationFlag(true)
            .status(ReferenceDataMock.getKeyworkerStatuses().get(KeyworkerStatus.ACTIVE.name()))
            .build()));
        when(keyworkerRepository.findById(5L)).thenReturn(Optional.of(LegacyKeyworker.builder().staffId(3L).autoAllocationFlag(true)
            .status(ReferenceDataMock.getKeyworkerStatuses().get(KeyworkerStatus.UNAVAILABLE_LONG_TERM_ABSENCE.name()))
            .build()));
        when(keyworkerRepository.findById(7L)).thenReturn(Optional.of(LegacyKeyworker.builder().staffId(3L).autoAllocationFlag(true)
            .status(ReferenceDataMock.getKeyworkerStatuses().get(KeyworkerStatus.ACTIVE.name()))
            .build()));

        when(repository.countByStaffIdAndPrisonIdAndActiveAndAllocationTypeIsNot(1L, TEST_AGENCY, true, PROVISIONAL)).thenReturn(0);
        when(repository.countByStaffIdAndPrisonIdAndActiveAndAllocationTypeIsNot(2L, TEST_AGENCY, true, PROVISIONAL)).thenReturn(2);
        when(repository.countByStaffIdAndPrisonIdAndActiveAndAllocationTypeIsNot(3L, TEST_AGENCY, true, PROVISIONAL)).thenReturn(1);
        when(repository.countByStaffIdAndPrisonIdAndActiveAndAllocationTypeIsNot(5L, TEST_AGENCY, true, PROVISIONAL)).thenReturn(0);
        when(repository.countByStaffIdAndPrisonIdAndActiveAndAllocationTypeIsNot(7L, TEST_AGENCY, true, PROVISIONAL)).thenReturn(2);

        when(nomisService.getAvailableKeyworkers(TEST_AGENCY)).thenReturn(keyworkers);
        // Invoke service method
        final var keyworkerList = service.getAvailableKeyworkers(TEST_AGENCY, true);

        // Verify response
        assertThat(keyworkerList).hasSize(4);
        assertThat(keyworkerList).extracting("numberAllocated").isEqualTo(ImmutableList.of(0, 0, 1, 2));
    }

    @Test
    void testGetAvailableKeyworkersNotMigrated() {
        final var keyworkers = ImmutableList.of(
            KeyworkerTestHelper.getKeyworker(11, 0, 0),
            KeyworkerTestHelper.getKeyworker(12, 0, 0),
            KeyworkerTestHelper.getKeyworker(13, 0, 0),
            KeyworkerTestHelper.getKeyworker(14, 0, 0)
        );

        when(nomisService.getAvailableKeyworkers(NON_MIGRATED_TEST_AGENCY)).thenReturn(keyworkers);

        final var allocatedKeyworkers = ImmutableList.of(
            KeyworkerTestHelper.getKeyworkerAllocations(12, "AA0001AB", NON_MIGRATED_TEST_AGENCY, LocalDateTime.now()),
            KeyworkerTestHelper.getKeyworkerAllocations(13, "AA0001AC", NON_MIGRATED_TEST_AGENCY, LocalDateTime.now()),
            KeyworkerTestHelper.getKeyworkerAllocations(14, "AA0001AD", NON_MIGRATED_TEST_AGENCY, LocalDateTime.now()),
            KeyworkerTestHelper.getKeyworkerAllocations(14, "AA0001AE", NON_MIGRATED_TEST_AGENCY, LocalDateTime.now())
        );

        when(nomisService.getCurrentAllocations(ImmutableList.of(11L, 12L, 13L, 14L), NON_MIGRATED_TEST_AGENCY)).thenReturn(allocatedKeyworkers);
        // Invoke service method
        final var keyworkerList = service.getAvailableKeyworkers(NON_MIGRATED_TEST_AGENCY, true);

        // Verify response
        assertThat(keyworkerList).hasSize(4);
        assertThat(keyworkerList).extracting("numberAllocated").isEqualTo(ImmutableList.of(0, 1, 1, 2));

        verify(prisonSupportedService, times(1)).isMigrated(eq(NON_MIGRATED_TEST_AGENCY));
    }

    @Test
    void testGetKeyworkersAvailableforAutoAllocation() {

        final var allocations = ImmutableList.of(
            KeyworkerTestHelper.getKeyworker(1, 0, CAPACITY_TIER_1),
            KeyworkerTestHelper.getKeyworker(2, 0, CAPACITY_TIER_1),
            KeyworkerTestHelper.getKeyworker(3, 0, CAPACITY_TIER_1),
            KeyworkerTestHelper.getKeyworker(4, 0, CAPACITY_TIER_1));

        when(keyworkerRepository.findById(1L))
            .thenReturn(Optional.of(LegacyKeyworker.builder()
                    .status(ReferenceDataMock.getKeyworkerStatuses().get(KeyworkerStatus.ACTIVE.name()))
                .staffId(1L)
                .autoAllocationFlag(true)
                .build()
            ));
        when(keyworkerRepository.findById(2L)).thenReturn(Optional.of(LegacyKeyworker.builder()
            .status(ReferenceDataMock.getKeyworkerStatuses().get(KeyworkerStatus.ACTIVE.name()))
            .staffId(2L)
            .autoAllocationFlag(true)
            .build()
        ));
        when(keyworkerRepository.findById(3L)).thenReturn(Optional.of(LegacyKeyworker.builder()
            .status(ReferenceDataMock.getKeyworkerStatuses().get(KeyworkerStatus.ACTIVE.name()))
            .staffId(3L)
            .autoAllocationFlag(true)
            .build()
        ));
        when(keyworkerRepository.findById(4L)).thenReturn(Optional.of(LegacyKeyworker.builder()
            .status(ReferenceDataMock.getKeyworkerStatuses().get(KeyworkerStatus.ACTIVE.name()))
            .staffId(4L)
            .autoAllocationFlag(false)
            .build()
        ));

        when(repository.countByStaffIdAndPrisonIdAndActiveAndAllocationTypeIsNot(1L, TEST_AGENCY, true, PROVISIONAL)).thenReturn(2);
        when(repository.countByStaffIdAndPrisonIdAndActiveAndAllocationTypeIsNot(2L, TEST_AGENCY, true, PROVISIONAL)).thenReturn(3);
        when(repository.countByStaffIdAndPrisonIdAndActiveAndAllocationTypeIsNot(3L, TEST_AGENCY, true, PROVISIONAL)).thenReturn(1);


        when(nomisService.getAvailableKeyworkers(TEST_AGENCY)).thenReturn(allocations);
        // Invoke service method
        final var keyworkerList = service.getKeyworkersAvailableForAutoAllocation(TEST_AGENCY);

        // Verify response
        assertThat(keyworkerList).hasSize(3);
        //should exclude staffid 4 - autoAllocationAllowed flag is false
        assertThat(keyworkerList).extracting("numberAllocated").isEqualTo(ImmutableList.of(1, 2, 3));
        assertThat(keyworkerList).extracting("autoAllocationAllowed").isEqualTo(ImmutableList.of(true, true, true));

    }

    @Test
    void testDeallocation() {
        final var offenderNo = "A1111AA";
        final var staffId = -1L;

        final var testOffenderKeyworker = getTestOffenderKeyworker(offenderNo, staffId);
        when(repository.findByActiveAndOffenderNo(true, offenderNo)).thenReturn(Collections.singletonList(testOffenderKeyworker));

        service.deallocate(offenderNo);

        verify(repository).findByActiveAndOffenderNo(true, offenderNo);
    }

    @Test
    void testDeallocationNoOffender() {
        final var offenderNo = "A1111AB";

        when(repository.findByActiveAndOffenderNo(true, offenderNo)).thenReturn(new ArrayList<>());

        assertThatThrownBy(() -> service.deallocate(offenderNo)).isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void testThatANewKeyworkerRecordIsInserted() {
        final long staffId = 1;
        final var prisonId = "LEI";
        final var capacity = 10;
        final var status = KeyworkerStatus.ACTIVE;

        final var argCap = ArgumentCaptor.forClass(LegacyKeyworker.class);

        when(keyworkerRepository.findById(staffId)).thenReturn(Optional.empty());

        service.addOrUpdate(staffId,
            prisonId, KeyworkerUpdateDto.builder().capacity(capacity).status(status).build());

        verify(keyworkerRepository, times(1)).save(argCap.capture());

        assertThat(argCap.getValue().getStaffId()).isEqualTo(staffId);
        assertThat(argCap.getValue().getCapacity()).isEqualTo(capacity);
        assertThat(argCap.getValue().getStatus().getCode()).isEqualTo(status.name());
    }

    @Test
    void testThatKeyworkerRecordIsUpdated() {
        final var status = KeyworkerStatus.UNAVAILABLE_ANNUAL_LEAVE;

        final var existingKeyWorker = LegacyKeyworker.builder()
            .staffId(TEST_STAFF_ID)
            .capacity(TEST_CAPACITY)
            .status(ReferenceDataMock.getKeyworkerStatuses().get(KeyworkerStatus.ACTIVE.name()))
            .build();

        when(keyworkerRepository.findById(TEST_STAFF_ID)).thenReturn(Optional.of(existingKeyWorker));

        service.addOrUpdate(TEST_STAFF_ID,
            TEST_AGENCY, KeyworkerUpdateDto.builder().capacity(TEST_CAPACITY).status(status).build());

        assertThat(existingKeyWorker.getStaffId()).isEqualTo(TEST_STAFF_ID);
        assertThat(existingKeyWorker.getCapacity()).isEqualTo(TEST_CAPACITY);
        assertThat(existingKeyWorker.getStatus().getCode()).isEqualTo(status.name());
    }

    @Test
    void testThatKeyworkerRecordIsUpdated_activeStatusAutoAllocation() {

        final var existingKeyWorker = LegacyKeyworker.builder()
            .staffId(TEST_STAFF_ID)
            .capacity(TEST_CAPACITY)
            .status(ReferenceDataMock.getKeyworkerStatuses().get(KeyworkerStatus.ACTIVE.name()))
            .autoAllocationFlag(false)
            .build();

        when(keyworkerRepository.findById(TEST_STAFF_ID)).thenReturn(Optional.of(existingKeyWorker));

        service.addOrUpdate(TEST_STAFF_ID,
            TEST_AGENCY, KeyworkerUpdateDto.builder().capacity(TEST_CAPACITY).status(KeyworkerStatus.ACTIVE).build());

        assertThat(existingKeyWorker.getStatus().getCode()).isEqualTo(KeyworkerStatus.ACTIVE.name());
        //auto allocation flag is updated to true for active status
        assertThat(existingKeyWorker.getAutoAllocationFlag()).isEqualTo(true);
    }

    @Test
    void testThatKeyworkerRecordIsUpdated_inactiveStatusAutoAllocation() {
        final var existingKeyWorker = LegacyKeyworker.builder()
            .staffId(TEST_STAFF_ID)
            .capacity(TEST_CAPACITY)
            .status(ReferenceDataMock.getKeyworkerStatuses().get(KeyworkerStatus.INACTIVE.name()))
            .autoAllocationFlag(false)
            .build();

        when(keyworkerRepository.findById(TEST_STAFF_ID)).thenReturn(Optional.of(existingKeyWorker));

        service.addOrUpdate(TEST_STAFF_ID,
            TEST_AGENCY, KeyworkerUpdateDto.builder().capacity(TEST_CAPACITY).status(KeyworkerStatus.INACTIVE).build());

        assertThat(existingKeyWorker.getStatus().getCode()).isEqualTo(KeyworkerStatus.INACTIVE.name());
        //auto allocation flag remains false for inactive status
        assertThat(existingKeyWorker.getAutoAllocationFlag()).isEqualTo(false);
    }

    @Test
    void testkeyworkerStatusChangeBehaviour_removeAllocations() {
        final var existingKeyWorker = LegacyKeyworker.builder()
            .staffId(TEST_STAFF_ID)
            .build();

        when(keyworkerRepository.findById(TEST_STAFF_ID)).thenReturn(Optional.of(existingKeyWorker));

        final var allocations = KeyworkerTestHelper.getAllocations(TEST_AGENCY, ImmutableSet.of("1", "2", "3"));
        when(repository.findByStaffIdAndPrisonIdAndActive(TEST_STAFF_ID, TEST_AGENCY, true)).thenReturn(allocations);

        service.addOrUpdate(TEST_STAFF_ID,
            TEST_AGENCY, KeyworkerUpdateDto.builder().capacity(1).status(KeyworkerStatus.UNAVAILABLE_LONG_TERM_ABSENCE).behaviour(KeyworkerStatusBehaviour.REMOVE_ALLOCATIONS_NO_AUTO).build());

        verify(repository, times(1)).findByStaffIdAndPrisonIdAndActive(TEST_STAFF_ID, TEST_AGENCY, true);
    }

    @Test
    void testkeyworkerStatusChangeBehaviour_keepAllocations() {
        final var existingKeyWorker = LegacyKeyworker.builder()
            .staffId(TEST_STAFF_ID)
            .build();

        when(keyworkerRepository.findById(TEST_STAFF_ID)).thenReturn(Optional.of(existingKeyWorker));

        service.addOrUpdate(TEST_STAFF_ID,
            TEST_AGENCY, KeyworkerUpdateDto.builder().capacity(1).status(KeyworkerStatus.ACTIVE).behaviour(KeyworkerStatusBehaviour.KEEP_ALLOCATIONS).build());

        verify(repository, never()).findByStaffIdAndPrisonIdAndActive(any(), any(), anyBoolean());
    }

    private OffenderKeyworker getTestOffenderKeyworker(final String offenderNo, final long staffId) {
        return OffenderKeyworker.builder()
            .prisonId(TEST_AGENCY)
            .offenderNo(offenderNo)
            .staffId(staffId)
            .allocationType(AllocationType.AUTO)
            .allocationReason(allocationReason(AllocationReason.AUTO))
            .build();
    }
}
