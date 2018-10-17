package uk.gov.justice.digital.hmpps.keyworker.services;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import uk.gov.justice.digital.hmpps.keyworker.dto.CaseNoteUsageDto;
import uk.gov.justice.digital.hmpps.keyworker.dto.KeyworkerStatsDto;
import uk.gov.justice.digital.hmpps.keyworker.repository.OffenderKeyworkerRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;

import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


@RunWith(MockitoJUnitRunner.class)
public class KeyworkerStatsServiceTest {

    @Mock
    private NomisService nomisService;

    @Mock
    private OffenderKeyworkerRepository repository;

    @Mock
    private PrisonSupportedService prisonSupportedService;

    private KeyworkerStatsService service;

    private LocalDate fromDate;
    private LocalDate toDate;

    private final static String TEST_AGENCY_ID = "LEI";
    private final static Long TEST_STAFF_ID = (long) -5;
    private final static String TEST_CASE_NOTE_TYPE = "KA";
    private final String TEST_SESSION_CASE_NOTE_SUBTYPE = "KS";
    private final String TEST_ENTRY_CASE_NOTE_SUBTYPE = "KA";

    @Before
    public void setUp() {

        service = new KeyworkerStatsService(nomisService, prisonSupportedService, repository);
        fromDate = LocalDate.now();
        toDate = LocalDate.now();
    }

    @Test(expected = NullPointerException.class)
    public void testShouldThrowIfRequiredParametersAreMissing() {
        service.getStatsForStaff(null, null, null, null);
    }

    @Test
    public void testThatNomisServiceIsCalledWithTheCorrectParameters() {
        when(nomisService.getCaseNoteUsage(
                Collections.singletonList(TEST_STAFF_ID), TEST_CASE_NOTE_TYPE, null, fromDate,toDate))
                    .thenReturn(new ArrayList<>());


        service.getStatsForStaff(TEST_STAFF_ID, TEST_AGENCY_ID, fromDate, toDate);

        verify(nomisService).getCaseNoteUsage(Collections.singletonList(TEST_STAFF_ID), TEST_CASE_NOTE_TYPE,
                null, fromDate, toDate);
    }

    @Test
    public void testThatTheResponseGetsMappedCorrectly() {
        ArrayList<CaseNoteUsageDto> usageCounts = new ArrayList<>();

        usageCounts.add(CaseNoteUsageDto.builder()
                .caseNoteSubType(TEST_CASE_NOTE_TYPE)
                .caseNoteSubType(TEST_SESSION_CASE_NOTE_SUBTYPE)
                .staffId(TEST_STAFF_ID)
                .numCaseNotes(10)
                .build());

        usageCounts.add(CaseNoteUsageDto.builder()
                .caseNoteSubType(TEST_CASE_NOTE_TYPE)
                .caseNoteSubType(TEST_ENTRY_CASE_NOTE_SUBTYPE)
                .staffId(TEST_STAFF_ID)
                .numCaseNotes(11)
                .build());


        when(nomisService.getCaseNoteUsage(
                Collections.singletonList(TEST_STAFF_ID), TEST_CASE_NOTE_TYPE, null, fromDate, toDate))
                    .thenReturn(usageCounts);

        KeyworkerStatsDto stats = service.getStatsForStaff(
                TEST_STAFF_ID,
                TEST_AGENCY_ID,
                fromDate,
                toDate);

        assertThat(stats.getCaseNoteEntryCount()).isEqualTo(11);
        assertThat(stats.getCaseNoteSessionCount()).isEqualTo(10);
        assertThat(stats.getComplianceRate()).isEqualTo(new BigDecimal("100.00"));
        assertThat(stats.getProjectedKeyworkerSessions()).isEqualTo(0);
    }
}
