package uk.gov.justice.digital.hmpps.keyworker.services;
import org.junit.Before;
import org.junit.Test;
import uk.gov.justice.digital.hmpps.keyworker.dto.CaseNoteUsageDto;
import uk.gov.justice.digital.hmpps.keyworker.dto.KeyworkerStatsDto;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;

import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.mockito.Mockito.*;


public class KeyworkerStatsServiceTest extends AbstractServiceTest {
    private NomisService nomisService;
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
        nomisService = mock(NomisService.class);
        service = new KeyworkerStatsService(nomisService);
        fromDate = LocalDate.now();
        toDate = LocalDate.now();
    }

    @Test(expected = NullPointerException.class)
    public void testShouldThrowIfRequiredParametersAreMissing() {
        service.getStatsFor(null, null, null, null);
    }

    @Test
    public void testThatNomisServiceIsCalledWithTheCorrectParameters() {
        when(nomisService.getCaseNoteUsage(
                Collections.singletonList(TEST_STAFF_ID), TEST_CASE_NOTE_TYPE, TEST_SESSION_CASE_NOTE_SUBTYPE, fromDate,toDate))
                    .thenReturn(new ArrayList<>());

        when(nomisService.getCaseNoteUsage(
                Collections.singletonList(TEST_STAFF_ID), TEST_CASE_NOTE_TYPE, TEST_ENTRY_CASE_NOTE_SUBTYPE, fromDate, toDate))
                    .thenReturn(new ArrayList<>());

        service.getStatsFor(TEST_STAFF_ID, TEST_AGENCY_ID, fromDate, toDate);

        verify(nomisService).getCaseNoteUsage(Collections.singletonList(TEST_STAFF_ID), TEST_CASE_NOTE_TYPE,
                TEST_SESSION_CASE_NOTE_SUBTYPE, fromDate, toDate);

        verify(nomisService).getCaseNoteUsage(Collections.singletonList(TEST_STAFF_ID), TEST_CASE_NOTE_TYPE,
                TEST_ENTRY_CASE_NOTE_SUBTYPE, fromDate, toDate);
    }

    @Test
    public void testThatTheResponseGetsMappedCorrectly() {
        ArrayList<CaseNoteUsageDto> sessions = new ArrayList<>();
        ArrayList<CaseNoteUsageDto> entries = new ArrayList<>();

        sessions.add(CaseNoteUsageDto.builder()
                .caseNoteSubType(TEST_CASE_NOTE_TYPE)
                .caseNoteSubType(TEST_SESSION_CASE_NOTE_SUBTYPE)
                .staffId(TEST_STAFF_ID)
                .numCaseNotes(10)
                .build());

        entries.add(CaseNoteUsageDto.builder()
                .caseNoteSubType(TEST_CASE_NOTE_TYPE)
                .caseNoteSubType(TEST_ENTRY_CASE_NOTE_SUBTYPE)
                .staffId(TEST_STAFF_ID)
                .numCaseNotes(11)
                .build());

        when(nomisService.getCaseNoteUsage(
                Collections.singletonList(TEST_STAFF_ID), TEST_CASE_NOTE_TYPE, TEST_SESSION_CASE_NOTE_SUBTYPE, fromDate, toDate))
                    .thenReturn(sessions);

        when(nomisService.getCaseNoteUsage(
                Collections.singletonList(TEST_STAFF_ID), TEST_CASE_NOTE_TYPE, TEST_ENTRY_CASE_NOTE_SUBTYPE, fromDate, toDate))
                    .thenReturn(entries);

        KeyworkerStatsDto stats = service.getStatsFor(
                TEST_STAFF_ID,
                TEST_AGENCY_ID,
                fromDate,
                toDate);

        assertThat(stats.getCaseNoteEntryCount()).isEqualTo(11);
        assertThat(stats.getCaseNoteSessionCount()).isEqualTo(10);
        assertThat(stats.getComplianceRate()).isEqualTo(0);
        assertThat(stats.getProjectedKeyworkerSessions()).isEqualTo(0);
    }
}
