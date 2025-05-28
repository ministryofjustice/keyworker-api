package uk.gov.justice.digital.hmpps.keyworker.services;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceDataRepository;
import uk.gov.justice.digital.hmpps.keyworker.dto.OffenderKeyworkerDto;
import uk.gov.justice.digital.hmpps.keyworker.exception.PrisonNotSupportedException;
import uk.gov.justice.digital.hmpps.keyworker.model.PrisonSupported;
import uk.gov.justice.digital.hmpps.keyworker.repository.OffenderKeyworkerRepository;
import uk.gov.justice.digital.hmpps.keyworker.repository.PrisonSupportedRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.anySet;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@RestClientTest(KeyworkerMigrationService.class)
class KeyworkerMigrationServiceTest extends AbstractServiceTest {
    private static final String TEST_AGENCY = "LEI";
    private static final int TEST_PAGE_SIZE = Integer.MAX_VALUE;
    private static final String INVALID_AGENCY_ID = "XXX";

    @Autowired
    private KeyworkerMigrationService service;

    @MockitoBean
    private NomisService nomisService;

    @MockitoBean
    private PrisonSupportedRepository prisonSupportedRepository;

    @MockitoBean
    private OffenderKeyworkerRepository offenderKeyworkerRepository;

    @MockitoBean
    private PrisonSupportedService prisonSupportedService;

    @MockitoBean
    private ReferenceDataRepository referenceDataRepository;

    @BeforeEach
    void setUp() {
        doThrow(new PrisonNotSupportedException(INVALID_AGENCY_ID)).when(prisonSupportedService).verifyPrisonMigrated(eq(INVALID_AGENCY_ID));
    }

    // When request made to check and migrate agency that is not eligible for migration
    // Then migration does not start and PrisonNotSupportedException is thrown
    @Test
    void testCheckAndMigrateOffenderKeyWorkerIneligibleAgency() {
        when(prisonSupportedService.isMigrated(eq(INVALID_AGENCY_ID))).thenThrow(PrisonNotSupportedException.class);
        assertThatThrownBy(() -> service.migrateKeyworkerByPrison(INVALID_AGENCY_ID)).isInstanceOf(PrisonNotSupportedException.class);
    }

    // When request made to check and migrate agency that is eligible for migration and has already been migrated
    // Then migration does not start but no error is thrown
    @Test
    void testCheckAndMigrateOffenderKeyWorkerAgencyAlreadyMigrated() {
        when(prisonSupportedService.isMigrated(eq(TEST_AGENCY))).thenReturn(true);

        service.migrateKeyworkerByPrison(TEST_AGENCY);

        verify(prisonSupportedService, times(1)).isMigrated(eq(TEST_AGENCY));
    }

    // When request made to check and migrate agency that is eligible for migration but has not yet been migrated
    // Then migration completes successfully
    @Test
    void testCheckAndMigrateOffenderKeyWorker() {
        final var count = 39L;

        final var testDtos = getTestOffenderKeyworkerDtos(count);

        when(nomisService.getOffenderKeyWorkerPage(TEST_AGENCY, 0, TEST_PAGE_SIZE)).thenReturn(testDtos);

        when(prisonSupportedRepository.findByPrisonCode(eq(TEST_AGENCY))).thenReturn(Optional.of(PrisonSupported.builder().prisonCode(TEST_AGENCY).build()));
        service.migrateKeyworkerByPrison(TEST_AGENCY);

        verify(prisonSupportedRepository, times(1)).findByPrisonCode(eq(TEST_AGENCY));
        verify(offenderKeyworkerRepository).saveAll(anySet());
    }

    private List<OffenderKeyworkerDto> getTestOffenderKeyworkerDtos(final long count) {
        final List<OffenderKeyworkerDto> dtoList = new ArrayList<>();

        for (long i = 1; i <= count; i++) {
            dtoList.add(OffenderKeyworkerDto.builder()
                    .agencyId(TEST_AGENCY)
                    .staffId(i)
                    .offenderNo("A" + (1000 + i) + "AA")
                    .active(RandomStringUtils.random(1, "YN"))
                    .build());
        }

        return dtoList;
    }
}
