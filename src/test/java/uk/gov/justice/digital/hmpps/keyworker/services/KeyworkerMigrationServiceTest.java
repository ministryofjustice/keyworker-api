package uk.gov.justice.digital.hmpps.keyworker.services;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.util.ReflectionTestUtils;
import uk.gov.justice.digital.hmpps.keyworker.dto.OffenderKeyworkerDto;
import uk.gov.justice.digital.hmpps.keyworker.exception.PrisonNotSupportedException;
import uk.gov.justice.digital.hmpps.keyworker.model.PrisonSupported;
import uk.gov.justice.digital.hmpps.keyworker.repository.BulkOffenderKeyworkerImporter;
import uk.gov.justice.digital.hmpps.keyworker.repository.PrisonSupportedRepository;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.*;

@RunWith(SpringRunner.class)
@RestClientTest(KeyworkerMigrationService.class)
public class KeyworkerMigrationServiceTest extends AbstractServiceTest {
    private static final String TEST_AGENCY = "LEI";
    private static final Long TEST_PAGE_SIZE = 25L;
    public static final String INVALID_AGENCY_ID = "XXX";

    @Autowired
    private KeyworkerMigrationService service;

    @MockBean
    private NomisService nomisService;

    @MockBean
    private PrisonSupportedRepository prisonSupportedRepository;

    @MockBean
    private BulkOffenderKeyworkerImporter importer;

    @MockBean
    private PrisonSupportedService prisonSupportedService;

    @Before
    public void setUp() throws Exception {
        ReflectionTestUtils.setField(service, "migrationPageSize", TEST_PAGE_SIZE);
        doThrow(new PrisonNotSupportedException(INVALID_AGENCY_ID)).when(prisonSupportedService).verifyPrisonMigrated(eq(INVALID_AGENCY_ID));
    }

    // When request made to check and migrate agency that is not eligible for migration
    // Then migration does not start and PrisonNotSupportedException is thrown
    @Test(expected = PrisonNotSupportedException.class)
    public void testCheckAndMigrateOffenderKeyWorkerIneligibleAgency() throws Exception {
        when(prisonSupportedService.isMigrated(eq(INVALID_AGENCY_ID))).thenThrow(PrisonNotSupportedException.class);
        service.migrateKeyworkerByPrison(INVALID_AGENCY_ID);
    }

    // When request made to check and migrate agency that is eligible for migration and has already been migrated
    // Then migration does not start but no error is thrown
    @Test
    public void testCheckAndMigrateOffenderKeyWorkerAgencyAlreadyMigrated() throws Exception {
        when(prisonSupportedService.isMigrated(eq(TEST_AGENCY))).thenReturn(true);

        service.migrateKeyworkerByPrison(TEST_AGENCY);

        verify(prisonSupportedService, times(1)).isMigrated(eq(TEST_AGENCY));
    }

    // When request made to check and migrate agency that is eligible for migration but has not yet been migrated
    // Then migration completes successfully
    @Test
    public void testCheckAndMigrateOffenderKeyWorker() throws Exception {
        final Long count = 39L;
        Long offset = 0L;
        int pageCount = 0;

        List<OffenderKeyworkerDto> testDtos = getTestOffenderKeyworkerDtos(TEST_AGENCY, count);

        do {
            int toIndex = Math.min(Long.valueOf(offset + TEST_PAGE_SIZE).intValue(), count.intValue());
            when(nomisService.getOffenderKeyWorkerPage(TEST_AGENCY, offset, TEST_PAGE_SIZE)).thenReturn(testDtos.subList(offset.intValue(), toIndex));
            offset += TEST_PAGE_SIZE;
            pageCount++;
        } while (offset <= count);

        when(prisonSupportedRepository.findOne(eq(TEST_AGENCY))).thenReturn(PrisonSupported.builder().prisonId(TEST_AGENCY).build());
        service.migrateKeyworkerByPrison(TEST_AGENCY);

        verify(prisonSupportedRepository, times(1)).findOne(eq(TEST_AGENCY));
        verify(importer, times(1)).importAll();
        verify(importer, times(pageCount)).translateAndStore(anyListOf(OffenderKeyworkerDto.class));
    }

    private List<OffenderKeyworkerDto> getTestOffenderKeyworkerDtos(String prisonId, long count) {
        List<OffenderKeyworkerDto> dtoList = new ArrayList<>();

        for (long i = 1; i <= count; i++) {
            dtoList.add(OffenderKeyworkerDto.builder()
                    .agencyId(prisonId)
                    .staffId(i)
                    .offenderNo("A" + (1000 + i) + "AA")
                    .active(RandomStringUtils.random(1, "YN"))
                    .build());
        }

        return dtoList;
    }
}