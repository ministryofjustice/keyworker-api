package uk.gov.justice.digital.hmpps.keyworker.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import uk.gov.justice.digital.hmpps.keyworker.dto.OffenderSummaryDto;
import uk.gov.justice.digital.hmpps.keyworker.dto.PagingAndSortingDto;
import uk.gov.justice.digital.hmpps.keyworker.exception.AgencyNotSupportedException;
import uk.gov.justice.digital.hmpps.keyworker.model.AllocationReason;
import uk.gov.justice.digital.hmpps.keyworker.model.AllocationType;
import uk.gov.justice.digital.hmpps.keyworker.model.OffenderKeyworker;
import uk.gov.justice.digital.hmpps.keyworker.repository.KeyworkerRepository;
import uk.gov.justice.digital.hmpps.keyworker.repository.OffenderKeyworkerRepository;
import uk.gov.justice.digital.hmpps.keyworker.security.AuthenticationFacade;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Test class for {@link KeyworkerService}.
 */
@RunWith(SpringRunner.class)
@RestClientTest(KeyworkerService.class)
public class KeyworkerServiceTest extends AbstractServiceTest {
    private static final String TEST_AGENCY = "LEI";
    private static final String TEST_USER = "VANILLA";
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
