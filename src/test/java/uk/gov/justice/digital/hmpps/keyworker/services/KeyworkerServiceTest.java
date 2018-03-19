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
import org.springframework.test.util.ReflectionTestUtils;
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
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;

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

    @Before
    public void setUp() {
        ReflectionTestUtils.setField(service, "supportedAgencies", Collections.singleton(TEST_AGENCY));
       // ReflectionTestUtils.setField(service, "restTemplate", restTemplate);
    }

    @Test
    public void testVerifyAgencySupportForSupportedAgency() {
        service.verifyAgencySupport(TEST_AGENCY);
    }

    @Test(expected = AgencyNotSupportedException.class)
    public void testVerifyAgencySupportForUnsupportedAgency() {
        service.verifyAgencySupport("XXX");
    }

    @Test
    public void testGetUnallocatedOffendersForSupportedAgencyNoneAllocated() throws Exception {
        Long count = 10L;
        Long offset = 0L;

        // Rest request mock setup
        String expectedUri = expandUriTemplate(KeyworkerService.URI_ACTIVE_OFFENDERS_BY_AGENCY, TEST_AGENCY);
        List<OffenderSummaryDto> testDtos = KeyworkerTestHelper.getOffenders(TEST_AGENCY, count);

        String testJsonResponse = objectMapper.writeValueAsString(testDtos);

        server.expect(requestTo(expectedUri))
                .andExpect(header(PagingAndSortingDto.HEADER_PAGE_OFFSET, offset.toString()))
                .andExpect(header(PagingAndSortingDto.HEADER_PAGE_LIMIT, TEST_PAGE_SIZE.toString()))
                .andRespond(withSuccess(testJsonResponse, MediaType.APPLICATION_JSON)
                        .headers(paginationHeaders(count, offset, TEST_PAGE_SIZE)));

        // Allocation processor mock setup - returning same DTOs
        when(processor.filterByUnallocated(anyListOf(OffenderSummaryDto.class))).thenReturn(testDtos);

        // Invoke service method
        List<OffenderSummaryDto> response = service.getUnallocatedOffenders(TEST_AGENCY, null, null);

        // Verify response
        assertThat(response.size()).isEqualTo(count.intValue());

        // Verify mocks
        verify(migrationService, times(1)).checkAndMigrateOffenderKeyWorker(eq(TEST_AGENCY));
        verify(processor, times(1)).filterByUnallocated(anyListOf(OffenderSummaryDto.class));

        server.verify();
    }

    @Test
    public void testGetUnallocatedOffendersForSupportedAgencyAllAllocated() throws Exception {
        Long count = 10L;
        Long offset = 0L;

        // Rest request mock setup
        String expectedUri = expandUriTemplate(KeyworkerService.URI_ACTIVE_OFFENDERS_BY_AGENCY, TEST_AGENCY);
        List<OffenderSummaryDto> testDtos = KeyworkerTestHelper.getOffenders(TEST_AGENCY, count);

        String testJsonResponse = objectMapper.writeValueAsString(testDtos);

        server.expect(requestTo(expectedUri))
                .andExpect(header(PagingAndSortingDto.HEADER_PAGE_OFFSET, offset.toString()))
                .andExpect(header(PagingAndSortingDto.HEADER_PAGE_LIMIT, TEST_PAGE_SIZE.toString()))
                .andRespond(withSuccess(testJsonResponse, MediaType.APPLICATION_JSON)
                        .headers(paginationHeaders(count, offset, TEST_PAGE_SIZE)));

        // Allocation processor mock setup - return empty list
        when(processor.filterByUnallocated(anyListOf(OffenderSummaryDto.class))).thenReturn(Collections.emptyList());

        // Invoke service method
        List<OffenderSummaryDto> response = service.getUnallocatedOffenders(TEST_AGENCY, null, null);

        // Verify response
        assertThat(response).isEmpty();

        // Verify mocks
        verify(migrationService, times(1)).checkAndMigrateOffenderKeyWorker(eq(TEST_AGENCY));
        verify(processor, times(1)).filterByUnallocated(anyListOf(OffenderSummaryDto.class));

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
        when(repository.findByActiveAndAgencyIdAndOffenderNoIn(true, TEST_AGENCY, testOffenderNos)).thenReturn(results);

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

    private void expectKeyworkerDetailsCall(long staffId, Integer CAPACITY, int ALLOCATIONS) throws JsonProcessingException {
        List<StaffLocationRoleDto> testDtos = Collections.singletonList(KeyworkerTestHelper.getStaffLocationRoleDto(staffId));
        String testJsonResponse = objectMapper.writeValueAsString(testDtos);

        server.expect(once(), requestTo(String.format("/staff/roles/%s/role/KW?staffId=%d", TEST_AGENCY, 5)))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(testJsonResponse, MediaType.APPLICATION_JSON));

        when(keyworkerRepository.findOne(staffId)).thenReturn(Keyworker.builder()
                .staffId(staffId)
                .capacity(CAPACITY)
                .status(KeyworkerStatus.UNAVAILABLE_ANNUAL_LEAVE)
                .build()
        );
        when(repository.countByStaffIdAndAgencyIdAndActive(staffId, TEST_AGENCY, true)).thenReturn(ALLOCATIONS);
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
        KeyworkerTestHelper.verifyKeyworkerDto(staffId, 11, ALLOCATIONS, KeyworkerStatus.UNAVAILABLE_ANNUAL_LEAVE, keyworkerDetails);
    }

    @Test
    public void testGetKeyworkerDetailsNoKeyworker() throws JsonProcessingException {
        final long staffId = 5L;
        List<StaffLocationRoleDto> testDtos = Collections.singletonList(KeyworkerTestHelper.getStaffLocationRoleDto(staffId));
        String testJsonResponse = objectMapper.writeValueAsString(testDtos);

        server.expect(once(), requestTo(String.format("/staff/roles/%s/role/KW?staffId=%d", TEST_AGENCY, 5)))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(testJsonResponse, MediaType.APPLICATION_JSON));

        when(keyworkerRepository.findOne(staffId)).thenReturn(null);
        when(repository.countByStaffIdAndAgencyIdAndActive(staffId, TEST_AGENCY, true)).thenReturn(null);

        KeyworkerDto keyworkerDetails = service.getKeyworkerDetails(TEST_AGENCY, staffId);

        server.verify();
        KeyworkerTestHelper.verifyKeyworkerDto(staffId, 11, null, KeyworkerStatus.ACTIVE, keyworkerDetails);
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
        when(repository.findByStaffIdAndAgencyIdAndActive(TEST_STAFF_ID, TEST_AGENCY, true)).thenReturn(allocations);

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
        when(repository.findByStaffIdAndAgencyIdAndActive(TEST_STAFF_ID, TEST_AGENCY, true)).thenReturn(allocations);

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
