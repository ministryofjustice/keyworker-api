package uk.gov.justice.digital.hmpps.keyworker.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import uk.gov.justice.digital.hmpps.keyworker.dto.*;
import uk.gov.justice.digital.hmpps.keyworker.exception.AgencyNotSupportedException;
import uk.gov.justice.digital.hmpps.keyworker.model.*;
import uk.gov.justice.digital.hmpps.keyworker.repository.KeyworkerRepository;
import uk.gov.justice.digital.hmpps.keyworker.repository.OffenderKeyworkerRepository;
import uk.gov.justice.digital.hmpps.keyworker.security.AuthenticationFacade;

import java.time.LocalDateTime;
import java.time.Month;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Test class for {@link KeyworkerService}.
 */
@RunWith(SpringRunner.class)
@RestClientTest(KeyworkerService.class)
public class KeyworkerServiceTest extends AbstractServiceTest {
    private static final String TEST_AGENCY = "LEI";
    private static final String TEST_USER = "VANILLA";
    private static final Long TEST_STAFF_ID = 67L;
    private static final Long TEST_PAGE_SIZE = 50L;
    private static final int TEST_CAPACITY = 5;

    @Autowired
    private KeyworkerService service;

    @Autowired
    private MockRestServiceServer server;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private KeyworkerMigrationService migrationService;

    @MockBean
    private AuthenticationFacade authenticationFacade;

    @MockBean
    private OffenderKeyworkerRepository repository;

    @MockBean
    private KeyworkerRepository keyworkerRepository;

    @MockBean
    private KeyworkerAllocationProcessor processor;

    @MockBean
    private AgencyValidation agencyValidation;

    @Before
    public void setup() {
        doThrow(new AgencyNotSupportedException("Agency [MDI] is not supported by this service.")).when(agencyValidation).verifyAgencySupport(eq("MDI"));
    }
    @Test
    public void testGetUnallocatedOffendersForSupportedAgencyNoneAllocated() throws Exception {
        Long count = 10L;
        Long offset = 0L;

        // Rest request mock setup
        String expectedUri = expandUriTemplate(KeyworkerService.URI_ACTIVE_OFFENDERS_BY_AGENCY, TEST_AGENCY);
        List<OffenderLocationDto> testDtos = KeyworkerTestHelper.getOffenders(TEST_AGENCY, count);

        String testJsonResponse = objectMapper.writeValueAsString(testDtos);

        server.expect(requestTo(expectedUri))
                .andExpect(header(PagingAndSortingDto.HEADER_PAGE_OFFSET, offset.toString()))
                .andExpect(header(PagingAndSortingDto.HEADER_PAGE_LIMIT, TEST_PAGE_SIZE.toString()))
                .andRespond(withSuccess(testJsonResponse, MediaType.APPLICATION_JSON)
                        .headers(paginationHeaders(count, offset, TEST_PAGE_SIZE)));

        // Allocation processor mock setup - returning same DTOs
        when(processor.filterByUnallocated(anyListOf(OffenderLocationDto.class))).thenReturn(testDtos);

        // Invoke service method
        List<OffenderLocationDto> response = service.getUnallocatedOffenders(TEST_AGENCY, null, null);

        // Verify response
        assertThat(response.size()).isEqualTo(count.intValue());

        // Verify mocks
        verify(migrationService, times(1)).checkAndMigrateOffenderKeyWorker(eq(TEST_AGENCY));
        verify(processor, times(1)).filterByUnallocated(anyListOf(OffenderLocationDto.class));

        server.verify();
    }

    @Test
    public void testGetUnallocatedOffendersForSupportedAgencyAllAllocated() throws Exception {
        Long count = 10L;
        Long offset = 0L;

        // Rest request mock setup
        String expectedUri = expandUriTemplate(KeyworkerService.URI_ACTIVE_OFFENDERS_BY_AGENCY, TEST_AGENCY);
        List<OffenderLocationDto> testDtos = KeyworkerTestHelper.getOffenders(TEST_AGENCY, count);

        String testJsonResponse = objectMapper.writeValueAsString(testDtos);

        server.expect(requestTo(expectedUri))
                .andExpect(header(PagingAndSortingDto.HEADER_PAGE_OFFSET, offset.toString()))
                .andExpect(header(PagingAndSortingDto.HEADER_PAGE_LIMIT, TEST_PAGE_SIZE.toString()))
                .andRespond(withSuccess(testJsonResponse, MediaType.APPLICATION_JSON)
                        .headers(paginationHeaders(count, offset, TEST_PAGE_SIZE)));

        // Allocation processor mock setup - return empty list
        when(processor.filterByUnallocated(anyListOf(OffenderLocationDto.class))).thenReturn(Collections.emptyList());

        // Invoke service method
        List<OffenderLocationDto> response = service.getUnallocatedOffenders(TEST_AGENCY, null, null);

        // Verify response
        assertThat(response).isEmpty();

        // Verify mocks
        verify(migrationService, times(1)).checkAndMigrateOffenderKeyWorker(eq(TEST_AGENCY));
        verify(processor, times(1)).filterByUnallocated(anyListOf(OffenderLocationDto.class));

        server.verify();
    }

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void testAllocateValidationAgencyInvalid() {
        KeyworkerAllocationDto dto = KeyworkerAllocationDto.builder().agencyId("MDI").build();
        thrown.expectMessage("Agency [MDI] is not supported by this service.");
        service.allocate(dto);
    }

    @Test
    public void testAllocateValidationOffenderMissing() {
        KeyworkerAllocationDto dto = KeyworkerAllocationDto.builder()
                .agencyId(TEST_AGENCY)
                .offenderNo(null)
                .build();
        thrown.expectMessage(String.format("Missing prisoner number."));

        service.allocate(dto);
    }

    @Test
    public void testAllocateValidationOffenderDoesNotExist() {
        final String offenderNo = "xxx";
        KeyworkerAllocationDto dto = KeyworkerAllocationDto.builder()
                .agencyId(TEST_AGENCY)
                .offenderNo(offenderNo)
                .staffId(5L)
                .build();
        server.expect(once(), requestTo(String.format("/locations/description/%s/inmates?keywords=%s", TEST_AGENCY, offenderNo)))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        thrown.expectMessage(String.format("Prisoner %s not found at agencyId %s using endpoint /locations/description/LEI/inmates?keywords=xxx.", offenderNo, TEST_AGENCY));

        service.allocate(dto);
    }

    @Test
    public void testAllocateValidationStaffIdMissing() throws JsonProcessingException {
        final String offenderNo = "A1111AA";
        final long staffId = -9999L;
        KeyworkerAllocationDto dto = KeyworkerAllocationDto.builder()
                .agencyId(TEST_AGENCY)
                .offenderNo(offenderNo)
                //.staffId(staffId)
                .build();

        thrown.expectMessage(String.format("Missing staff id.", staffId, TEST_AGENCY));

        service.allocate(dto);
    }

    @Test
    public void testAllocateValidationStaffDoesNotExist() throws JsonProcessingException {
        final String offenderNo = "A1111AA";
        final long staffId = -9999L;
        KeyworkerAllocationDto dto = KeyworkerAllocationDto.builder()
                .agencyId(TEST_AGENCY)
                .offenderNo(offenderNo)
                .staffId(staffId)
                .build();

        String offender1Uri = expandUriTemplate(KeyworkerService.URI_ACTIVE_OFFENDER_BY_AGENCY, TEST_AGENCY, offenderNo);
        String response = objectMapper.writeValueAsString(ImmutableList.of(KeyworkerTestHelper.getOffender(61, TEST_AGENCY, offenderNo, true)));
        server.expect(requestTo(offender1Uri)).andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

        final String kwUri = String.format("/staff/roles/%s/role/KW?staffId=%d", TEST_AGENCY, staffId);
        server.expect(once(), requestTo(kwUri)).andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        String staffUri = expandUriTemplate(KeyworkerService.URI_STAFF, staffId);
        server.expect(requestTo(staffUri)).andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        thrown.expectMessage(String.format("Keyworker %d not found at agencyId %s.", staffId, TEST_AGENCY));

        service.allocate(dto);
    }

    @Test
    public void testAllocateKeyworkerAllocationDto() throws JsonProcessingException {
        final String offenderNo = "A1111AA";
        final long staffId = 5;
        KeyworkerAllocationDto dto = KeyworkerAllocationDto.builder()
                .agencyId(TEST_AGENCY)
                .offenderNo(offenderNo)
                .staffId(staffId)
                .deallocationReason(DeallocationReason.RELEASED)
                .build();

        String offender1Uri = expandUriTemplate(KeyworkerService.URI_ACTIVE_OFFENDER_BY_AGENCY, TEST_AGENCY, offenderNo);
        String response = objectMapper.writeValueAsString(ImmutableList.of(KeyworkerTestHelper.getOffender(61, TEST_AGENCY, offenderNo, true)));
        server.expect(requestTo(offender1Uri)).andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

        final String kwUri = String.format("/staff/roles/%s/role/KW?staffId=%d", TEST_AGENCY, staffId);
        server.expect(requestTo(kwUri)).andRespond(withSuccess("[{}]", MediaType.APPLICATION_JSON));

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
    public void testGetOffenders() throws Exception {

        final LocalDateTime time1 = LocalDateTime.of(2018, Month.FEBRUARY, 26, 6, 0);
        final LocalDateTime time2 = LocalDateTime.of(2018, Month.FEBRUARY, 27, 6, 0);
        OffenderKeyworker offender1 = OffenderKeyworker.builder()
                .offenderKeyworkerId(11L)
                .offenderNo("offender1")
                .staffId(21L)
                .agencyId(TEST_AGENCY)
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
        when(repository.existsByAgencyId(TEST_AGENCY)).thenReturn(true);
        when(repository.findByActiveAndAgencyIdAndOffenderNoInAndAllocationTypeIsNot(true, TEST_AGENCY, testOffenderNos, AllocationType.PROVISIONAL)).thenReturn(results);

        final List<OffenderKeyworkerDto> offenders = service.getOffenders(TEST_AGENCY, testOffenderNos);

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
    public void testGetKeyworkerDetails() throws JsonProcessingException {
        final long staffId = 5L;
        final int CAPACITY = 10;
        final int ALLOCATIONS = 4;
        expectKeyworkerDetailsCall(staffId, CAPACITY, ALLOCATIONS);

        KeyworkerDto keyworkerDetails = service.getKeyworkerDetails(TEST_AGENCY, staffId);

        server.verify();
        KeyworkerTestHelper.verifyKeyworkerDto(staffId, CAPACITY, ALLOCATIONS, KeyworkerStatus.UNAVAILABLE_ANNUAL_LEAVE, keyworkerDetails);
    }

    @Test
    public void testGetActiveKeyworkerForOffender() throws JsonProcessingException {
        final String offenderNo = "X5555XX";
        final long staffId = 5L;
        expectGetActiveKeyworkerForOffenderCall(offenderNo, staffId, true);

        Optional<KeyworkerDto> keyworkerDetails = service.getCurrentKeyworkerForPrisoner(TEST_AGENCY, offenderNo);

        server.verify();
        KeyworkerTestHelper.verifyBasicKeyworkerDto(keyworkerDetails.get(), staffId, "First", "Last");
    }

    @Test
    public void testGetActiveKeyworkerForOffenderNonYetMigrated() throws JsonProcessingException {
        final String offenderNo = "X5555YY";
        final long staffId = 6L;
        KeyworkerDto expectedKeyworkerDto = expectGetActiveKeyworkerForOffenderCall(offenderNo, staffId, false);

        Optional<KeyworkerDto> keyworkerDetails = service.getCurrentKeyworkerForPrisoner(TEST_AGENCY, offenderNo);

        server.verify();
        KeyworkerTestHelper.verifyBasicKeyworkerDto(keyworkerDetails.get(), staffId, expectedKeyworkerDto.getFirstName(), expectedKeyworkerDto.getLastName());
    }

    private KeyworkerDto expectGetActiveKeyworkerForOffenderCall(String offenderNo, long staffId, boolean agencyMigrated) throws JsonProcessingException {

        when(repository.existsByAgencyId(TEST_AGENCY)).thenReturn(agencyMigrated);
        if (agencyMigrated) {
            when(repository.findByOffenderNoAndActive(offenderNo, true)).thenReturn(OffenderKeyworker.builder()
                    .staffId(staffId)
                    .build()
            );
            expectBasicStaffApiCall(staffId);
            return null;

        } else {
            KeyworkerDto keyworkerDto = KeyworkerTestHelper.getKeyworker(staffId, 1);
            String testJsonResponse = objectMapper.writeValueAsString(keyworkerDto);

            server.expect(once(), requestTo(String.format("/bookings/offenderNo/%s/key-worker", offenderNo)))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withSuccess(testJsonResponse, MediaType.APPLICATION_JSON));
            return keyworkerDto;
        }
    }

    private void expectKeyworkerDetailsCall(long staffId, Integer CAPACITY, int ALLOCATIONS) throws JsonProcessingException {
        expectStaffRoleApiCall(staffId);

        when(keyworkerRepository.findOne(staffId)).thenReturn(Keyworker.builder()
                .staffId(staffId)
                .capacity(CAPACITY)
                .status(KeyworkerStatus.UNAVAILABLE_ANNUAL_LEAVE)
                .autoAllocationFlag(true)
                .build()
        );
        when(repository.countByStaffIdAndAgencyIdAndActive(staffId, TEST_AGENCY, true)).thenReturn(ALLOCATIONS);
    }

    private void expectStaffRoleApiCall(long staffId) throws JsonProcessingException {
        List<StaffLocationRoleDto> testDtos = Collections.singletonList(KeyworkerTestHelper.getStaffLocationRoleDto(staffId));
        String testJsonResponse = objectMapper.writeValueAsString(testDtos);

        server.expect(once(), requestTo(String.format("/staff/roles/%s/role/KW?staffId=%d", TEST_AGENCY, staffId)))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(testJsonResponse, MediaType.APPLICATION_JSON));
    }

    private void expectBasicStaffApiCall(long staffId) throws JsonProcessingException {
        StaffLocationRoleDto staffLocationRoleDto = KeyworkerTestHelper.getStaffLocationRoleDto(staffId);
        String testJsonResponse = objectMapper.writeValueAsString(staffLocationRoleDto);

        server.expect(once(), requestTo(String.format("/staff/%d", staffId)))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(testJsonResponse, MediaType.APPLICATION_JSON));
    }

    @Test(expected = HttpClientErrorException.class)
    public void testGetKeyworkerDetails404() throws JsonProcessingException {
        final long staffId = 5L;
        server.expect(once(), requestTo(String.format("/staff/roles/%s/role/KW?staffId=%d", TEST_AGENCY, staffId)))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        service.getKeyworkerDetails(TEST_AGENCY, staffId);
    }

    @Test
    public void testGetKeyworkerDetailsNoCapacity() throws JsonProcessingException {
        final long staffId = 5L;
        final int ALLOCATIONS = 4;
        expectKeyworkerDetailsCall(staffId, null, ALLOCATIONS);

        KeyworkerDto keyworkerDetails = service.getKeyworkerDetails(TEST_AGENCY, staffId);

        server.verify();
        KeyworkerTestHelper.verifyKeyworkerDto(staffId, 6, ALLOCATIONS, KeyworkerStatus.UNAVAILABLE_ANNUAL_LEAVE, keyworkerDetails);
    }

    @Test
    public void testGetKeyworkerDetailsNoKeyworker() throws JsonProcessingException {
        final long staffId = 5L;
        expectStaffRoleApiCall(staffId);

        when(keyworkerRepository.findOne(staffId)).thenReturn(null);
        when(repository.countByStaffIdAndAgencyIdAndActive(staffId, TEST_AGENCY, true)).thenReturn(null);

        KeyworkerDto keyworkerDetails = service.getKeyworkerDetails(TEST_AGENCY, staffId);

        server.verify();
        KeyworkerTestHelper.verifyKeyworkerDto(staffId, 6, null, KeyworkerStatus.ACTIVE, keyworkerDetails);
    }

    @Test
    public void testGetAllocationsForKeyworkerWithOffenderDetails() throws Exception {

        String offender1Uri = expandUriTemplate(KeyworkerService.URI_ACTIVE_OFFENDER_BY_AGENCY, TEST_AGENCY, "1");
        String offender2Uri = expandUriTemplate(KeyworkerService.URI_ACTIVE_OFFENDER_BY_AGENCY, TEST_AGENCY, "2");
        String offender3Uri = expandUriTemplate(KeyworkerService.URI_ACTIVE_OFFENDER_BY_AGENCY, TEST_AGENCY, "3");

        String testJsonResponseOffender1 = objectMapper.writeValueAsString(ImmutableList.of(KeyworkerTestHelper.getOffender(61, TEST_AGENCY, "1",true)));
        String testJsonResponseOffender2 = objectMapper.writeValueAsString(ImmutableList.of(KeyworkerTestHelper.getOffender(62, TEST_AGENCY, "2",true)));
        String testJsonResponseOffender3 = objectMapper.writeValueAsString(ImmutableList.of(KeyworkerTestHelper.getOffender(63, TEST_AGENCY, "3",true)));

        final List<OffenderKeyworker> allocations = KeyworkerTestHelper.getAllocations(TEST_AGENCY, ImmutableSet.of("1", "2", "3"));

        // Mock allocation lookup
        when(repository.findByStaffIdAndAgencyIdAndActiveAndAllocationTypeIsNot(TEST_STAFF_ID, TEST_AGENCY, true, AllocationType.PROVISIONAL)).thenReturn(allocations);

        server.expect(requestTo(offender1Uri))
                .andRespond(withSuccess(testJsonResponseOffender1, MediaType.APPLICATION_JSON)
                        );

        server.expect(requestTo(offender2Uri))
                .andRespond(withSuccess(testJsonResponseOffender2, MediaType.APPLICATION_JSON)
                );

        server.expect(requestTo(offender3Uri))
                .andRespond(withSuccess(testJsonResponseOffender3, MediaType.APPLICATION_JSON)
                );

        // Invoke service method
        List<KeyworkerAllocationDetailsDto> allocationList = service.getAllocationsForKeyworkerWithOffenderDetails(TEST_AGENCY, TEST_STAFF_ID);

        // Verify response
        assertThat(allocationList).hasSize(3);
        assertThat(allocationList).extracting("bookingId").isEqualTo(ImmutableList.of(61L,62L,63L));

        // Verify mocks
        verify(migrationService, times(1)).checkAndMigrateOffenderKeyWorker(eq(TEST_AGENCY));

        server.verify();
    }

    @Test
    public void testGetAllocationsForKeyworkerWithOffenderDetails_NoAssociatedEliteBookingRecord() throws Exception {

        String offender1Uri = expandUriTemplate(KeyworkerService.URI_ACTIVE_OFFENDER_BY_AGENCY, TEST_AGENCY, "1");
        //Elite search responds with empty list for this offender
        String offender2Uri = expandUriTemplate(KeyworkerService.URI_ACTIVE_OFFENDER_BY_AGENCY, TEST_AGENCY, "2");
        String offender3Uri = expandUriTemplate(KeyworkerService.URI_ACTIVE_OFFENDER_BY_AGENCY, TEST_AGENCY, "3");

        String testJsonResponseOffender1 = objectMapper.writeValueAsString(ImmutableList.of(KeyworkerTestHelper.getOffender(61, TEST_AGENCY, "1",true)));
        String testJsonResponseOffender3 = objectMapper.writeValueAsString(ImmutableList.of(KeyworkerTestHelper.getOffender(63, TEST_AGENCY, "3",true)));

        final List<OffenderKeyworker> allocations = KeyworkerTestHelper.getAllocations(TEST_AGENCY, ImmutableSet.of("1", "2", "3"));

        // Mock allocation lookup
        when(repository.findByStaffIdAndAgencyIdAndActiveAndAllocationTypeIsNot(TEST_STAFF_ID, TEST_AGENCY, true, AllocationType.PROVISIONAL)).thenReturn(allocations);

        server.expect(requestTo(offender1Uri))
                .andRespond(withSuccess(testJsonResponseOffender1, MediaType.APPLICATION_JSON)
                );

        server.expect(requestTo(offender2Uri))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON)
                );

        server.expect(requestTo(offender3Uri))
                .andRespond(withSuccess(testJsonResponseOffender3, MediaType.APPLICATION_JSON)
                );

        // Invoke service method
        List<KeyworkerAllocationDetailsDto> allocationList = service.getAllocationsForKeyworkerWithOffenderDetails(TEST_AGENCY, TEST_STAFF_ID);

        // Verify response
        assertThat(allocationList).hasSize(2);
        assertThat(allocationList).extracting("bookingId").isEqualTo(ImmutableList.of(61L,63L));

        // Verify mocks
        verify(migrationService, times(1)).checkAndMigrateOffenderKeyWorker(eq(TEST_AGENCY));

        server.verify();
    }


    @Test
    public void testGetAvailableKeyworkers() throws Exception {
        String availableKeyworkersUri = expandUriTemplate(KeyworkerService.URI_AVAILABLE_KEYWORKERS, TEST_AGENCY);

        String testJsonResponseKeyworkers = objectMapper.writeValueAsString(ImmutableList.of(KeyworkerTestHelper.getKeyworker(1, 0),
                KeyworkerTestHelper.getKeyworker(2, 0),
                KeyworkerTestHelper.getKeyworker(3, 0)));


        when(keyworkerRepository.findOne(1l)).thenReturn(Keyworker.builder().staffId(1l).autoAllocationFlag(true).build());
        when(keyworkerRepository.findOne(2l)).thenReturn(Keyworker.builder().staffId(2l).autoAllocationFlag(true).build());
        when(keyworkerRepository.findOne(3l)).thenReturn(Keyworker.builder().staffId(3l).autoAllocationFlag(true).build());

        when(repository.countByStaffIdAndAgencyIdAndActive(1l, TEST_AGENCY, true)).thenReturn(2);
        when(repository.countByStaffIdAndAgencyIdAndActive(2l, TEST_AGENCY, true)).thenReturn(3);
        when(repository.countByStaffIdAndAgencyIdAndActive(3l, TEST_AGENCY, true)).thenReturn(1);

        server.expect(requestTo(availableKeyworkersUri))
                .andRespond(withSuccess(testJsonResponseKeyworkers, MediaType.APPLICATION_JSON)
                );

        // Invoke service method
        List<KeyworkerDto> keyworkerList = service.getAvailableKeyworkers(TEST_AGENCY);

        // Verify response
        assertThat(keyworkerList).hasSize(3);
        assertThat(keyworkerList).extracting("numberAllocated").isEqualTo(ImmutableList.of(1,2,3));

        server.verify();
    }

    @Test
    public void testGetKeyworkersAvailableforAutoAllocation() throws Exception {
        String availableKeyworkersUri = expandUriTemplate(KeyworkerService.URI_AVAILABLE_KEYWORKERS, TEST_AGENCY);

        String testJsonResponseKeyworkers = objectMapper.writeValueAsString(ImmutableList.of(KeyworkerTestHelper.getKeyworker(1, 0),
                KeyworkerTestHelper.getKeyworker(2, 0),
                KeyworkerTestHelper.getKeyworker(3, 0),
                KeyworkerTestHelper.getKeyworker(4, 0)));


        when(keyworkerRepository.findOne(1l)).thenReturn(Keyworker.builder().staffId(1l).autoAllocationFlag(true).build());
        when(keyworkerRepository.findOne(2l)).thenReturn(Keyworker.builder().staffId(2l).autoAllocationFlag(true).build());
        when(keyworkerRepository.findOne(3l)).thenReturn(Keyworker.builder().staffId(3l).autoAllocationFlag(true).build());
        when(keyworkerRepository.findOne(4l)).thenReturn(Keyworker.builder().staffId(4l).autoAllocationFlag(false).build());

        when(repository.countByStaffIdAndAgencyIdAndActive(1l, TEST_AGENCY, true)).thenReturn(2);
        when(repository.countByStaffIdAndAgencyIdAndActive(2l, TEST_AGENCY, true)).thenReturn(3);
        when(repository.countByStaffIdAndAgencyIdAndActive(3l, TEST_AGENCY, true)).thenReturn(1);

        server.expect(requestTo(availableKeyworkersUri))
                .andRespond(withSuccess(testJsonResponseKeyworkers, MediaType.APPLICATION_JSON)
                );

        // Invoke service method
        List<KeyworkerDto> keyworkerList = service.getKeyworkersAvailableforAutoAllocation(TEST_AGENCY);

        // Verify response
        assertThat(keyworkerList).hasSize(3);
        //should exclude staffid 4 - autoAllocationAllowed flag is false
        assertThat(keyworkerList).extracting("numberAllocated").isEqualTo(ImmutableList.of(1,2,3));
        assertThat(keyworkerList).extracting("autoAllocationAllowed").isEqualTo(ImmutableList.of(true,true,true));

        server.verify();
    }

    @Test
    public void testThatANewKeyworkerRecordIsInserted() {
        final long staffId = 1;
        final String agencyId = "LEI";
        final int capacity = 10;
        final KeyworkerStatus status = KeyworkerStatus.ACTIVE;

        ArgumentCaptor<Keyworker> argCap = ArgumentCaptor.forClass(Keyworker.class);

        when(keyworkerRepository.findOne(staffId)).thenReturn(null);

        service.addOrUpdate(staffId,
                agencyId, KeyworkerUpdateDto.builder().capacity(capacity).status(status).build());

        verify(keyworkerRepository, times(1)).save(argCap.capture());

        assertThat(argCap.getValue().getStaffId()).isEqualTo(staffId);
        assertThat(argCap.getValue().getCapacity()).isEqualTo(capacity);
        assertThat(argCap.getValue().getStatus()).isEqualTo(status);
    }

    @Test
    public void testThatKeyworkerRecordIsUpdated() {
        final KeyworkerStatus status = KeyworkerStatus.UNAVAILABLE_SUSPENDED;

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
        when(repository.findByStaffIdAndAgencyIdAndActive(TEST_STAFF_ID, TEST_AGENCY, true)).thenReturn(allocations);

        service.addOrUpdate(TEST_STAFF_ID,
                TEST_AGENCY, KeyworkerUpdateDto.builder().capacity(1).status(KeyworkerStatus.UNAVAILABLE_SUSPENDED).behaviour(KeyworkerStatusBehaviour.REMOVE_ALLOCATIONS_NO_AUTO).build());

        verify(repository, times(1)).findByStaffIdAndAgencyIdAndActive(TEST_STAFF_ID, TEST_AGENCY, true);
    }

    @Test
    public void testkeyworkerStatusChangeBehaviour_keepAllocations() {
        final Keyworker existingKeyWorker = Keyworker.builder()
                .staffId(TEST_STAFF_ID)
                .build();

        when(keyworkerRepository.findOne(TEST_STAFF_ID)).thenReturn(existingKeyWorker);

        service.addOrUpdate(TEST_STAFF_ID,
                TEST_AGENCY, KeyworkerUpdateDto.builder().capacity(1).status(KeyworkerStatus.ACTIVE).behaviour(KeyworkerStatusBehaviour.KEEP_ALLOCATIONS).build());

        verify(repository, never()).findByStaffIdAndAgencyIdAndActive(any(), any(), anyBoolean());
    }

    private OffenderKeyworker getTestOffenderKeyworker(String agencyId, String offenderNo, long staffId) {
        return OffenderKeyworker.builder()
                .agencyId(agencyId)
                .offenderNo(offenderNo)
                .staffId(staffId)
                .allocationType(AllocationType.AUTO)
                .allocationReason(AllocationReason.AUTO)
                .build();
    }
}
