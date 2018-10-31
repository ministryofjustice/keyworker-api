package uk.gov.justice.digital.hmpps.keyworker.services;

import com.microsoft.applicationinsights.TelemetryClient;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import uk.gov.justice.digital.hmpps.keyworker.dto.CaseNoteUsagePrisonersDto;
import uk.gov.justice.digital.hmpps.keyworker.dto.KeyworkerStatsDto;
import uk.gov.justice.digital.hmpps.keyworker.dto.Prison;
import uk.gov.justice.digital.hmpps.keyworker.model.OffenderKeyworker;
import uk.gov.justice.digital.hmpps.keyworker.repository.OffenderKeyworkerRepository;
import uk.gov.justice.digital.hmpps.keyworker.repository.PrisonKeyWorkerStatisticRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.justice.digital.hmpps.keyworker.services.KeyworkerService.*;


@RunWith(MockitoJUnitRunner.class)
public class KeyworkerStatsServiceTest {

    @Mock
    private NomisService nomisService;

    @Mock
    private OffenderKeyworkerRepository repository;

    @Mock
    private PrisonKeyWorkerStatisticRepository statisticRepository;

    @Mock
    private PrisonSupportedService prisonSupportedService;

    @Mock
    private TelemetryClient telemetryClient;

    private KeyworkerStatsService service;

    private LocalDate fromDate;
    private LocalDate toDate;

    private final static String TEST_AGENCY_ID = "LEI";
    private final static Long TEST_STAFF_ID = (long) -5;
    private final static Long TEST_STAFF_ID2 = (long) -3;
    private final static List<String> offenderNos = Arrays.asList( "A9876RS","A1176RS","A5576RS" );
    private final static String inactiveOffender = "B1176RS";
    private final static String activeInFuture = "B8876RS";
    private final static List<String> otherOffenderNos = Arrays.asList( "B9876RS","B5576RS","B5886RS","C5576RS","C5886RS","C8876RS" );

    @Before
    public void setUp() {
        service = new KeyworkerStatsService(nomisService, prisonSupportedService, repository, statisticRepository, telemetryClient);
        toDate = LocalDate.now();
        fromDate = toDate.minusMonths(1);
        when(prisonSupportedService.getPrisonDetail(TEST_AGENCY_ID)).thenReturn(Prison.builder().kwSessionFrequencyInWeeks(1).build());
    }

    @Test(expected = NullPointerException.class)
    public void testShouldThrowIfRequiredParametersAreMissing() {
        service.getStatsForStaff(null, null, null, null);
    }


    @Test
    public void testThatCorrectCalculationsAreMadeForAnActiveSetOfOffenders() {

        when(repository.findByStaffIdAndPrisonId(TEST_STAFF_ID, TEST_AGENCY_ID)).thenReturn(Arrays.asList(
                OffenderKeyworker.builder()
                        .offenderNo(offenderNos.get(0))
                        .staffId(TEST_STAFF_ID)
                        .assignedDateTime(fromDate.plusDays(1).atStartOfDay())
                        .expiryDateTime(toDate.minusDays(5).atStartOfDay())
                        .build(),
                OffenderKeyworker.builder()
                        .offenderNo(offenderNos.get(1))
                        .staffId(TEST_STAFF_ID)
                        .assignedDateTime(fromDate.plusDays(20).atStartOfDay())
                        .expiryDateTime(toDate.minusDays(1).atStartOfDay())
                        .build(),
                OffenderKeyworker.builder()
                        .offenderNo(offenderNos.get(2))
                        .staffId(TEST_STAFF_ID)
                        .assignedDateTime(toDate.minusWeeks(1).atStartOfDay())
                        .expiryDateTime(null)
                        .build()
        ));

        final List<CaseNoteUsagePrisonersDto> usageCounts = new ArrayList<>();

        usageCounts.add(CaseNoteUsagePrisonersDto.builder()
                .caseNoteSubType(KEYWORKER_CASENOTE_TYPE)
                .caseNoteSubType(KEYWORKER_SESSION_SUB_TYPE)
                .offenderNo(offenderNos.get(0))
                .numCaseNotes(2)
                .build());

        usageCounts.add(CaseNoteUsagePrisonersDto.builder()
                .caseNoteSubType(KEYWORKER_CASENOTE_TYPE)
                .caseNoteSubType(KEYWORKER_ENTRY_SUB_TYPE)
                .offenderNo(offenderNos.get(0))
                .numCaseNotes(3)
                .build());

        usageCounts.add(CaseNoteUsagePrisonersDto.builder()
                .caseNoteSubType(KEYWORKER_CASENOTE_TYPE)
                .caseNoteSubType(KEYWORKER_SESSION_SUB_TYPE)
                .offenderNo(offenderNos.get(1))
                .numCaseNotes(1)
                .build());

        usageCounts.add(CaseNoteUsagePrisonersDto.builder()
                .caseNoteSubType(KEYWORKER_CASENOTE_TYPE)
                .caseNoteSubType(KEYWORKER_ENTRY_SUB_TYPE)
                .offenderNo(offenderNos.get(1))
                .numCaseNotes(2)
                .build());

        usageCounts.add(CaseNoteUsagePrisonersDto.builder()
                .caseNoteSubType(KEYWORKER_CASENOTE_TYPE)
                .caseNoteSubType(KEYWORKER_SESSION_SUB_TYPE)
                .offenderNo(offenderNos.get(2))
                .numCaseNotes(1)
                .build());


        when(nomisService.getCaseNoteUsageForPrisoners( eq(offenderNos), eq(TEST_STAFF_ID), eq(KEYWORKER_CASENOTE_TYPE), isNull(String.class), eq(fromDate), eq(toDate), eq(false)))
                .thenReturn(usageCounts);

        KeyworkerStatsDto stats = service.getStatsForStaff(
                TEST_STAFF_ID,
                TEST_AGENCY_ID,
                fromDate,
                toDate);

        assertThat(stats.getCaseNoteEntryCount()).isEqualTo(5);
        assertThat(stats.getCaseNoteSessionCount()).isEqualTo(4);
        assertThat(stats.getProjectedKeyworkerSessions()).isEqualTo(5);
        assertThat(stats.getComplianceRate()).isEqualTo(new BigDecimal("80.00"));

        verify(nomisService).getCaseNoteUsageForPrisoners( eq(offenderNos), eq(TEST_STAFF_ID), eq(KEYWORKER_CASENOTE_TYPE), isNull(String.class), eq(fromDate), eq(toDate), eq(false));
    }

    @Test
    public void testThatNoActiveOffendersAreNotConsideredAndLimitedOffenderAssignmentIsHandled() {

        when(repository.findByStaffIdAndPrisonId(TEST_STAFF_ID2, TEST_AGENCY_ID)).thenReturn(Arrays.asList(
                OffenderKeyworker.builder() // Active for 3 weeks
                        .offenderNo(otherOffenderNos.get(0))
                        .staffId(TEST_STAFF_ID2)
                        .assignedDateTime(fromDate.minusWeeks(1).atStartOfDay())
                        .expiryDateTime(toDate.minusWeeks(1).atStartOfDay())
                        .build(),
                OffenderKeyworker.builder() // Inactive at this period
                        .offenderNo(inactiveOffender)
                        .staffId(TEST_STAFF_ID2)
                        .assignedDateTime(fromDate.minusMonths(2).atStartOfDay())
                        .expiryDateTime(toDate.minusMonths(1).minusDays(1).atStartOfDay())
                        .build(),
                OffenderKeyworker.builder() // Active in Last week of period
                        .offenderNo(otherOffenderNos.get(1))
                        .staffId(TEST_STAFF_ID2)
                        .assignedDateTime(toDate.minusWeeks(1).atStartOfDay())
                        .active(true)
                        .expiryDateTime(null)
                        .build(),
                OffenderKeyworker.builder() // Active for 1 week in middle of period
                        .offenderNo(otherOffenderNos.get(2))
                        .staffId(TEST_STAFF_ID2)
                        .assignedDateTime(fromDate.plusWeeks(2).atStartOfDay())
                        .expiryDateTime(toDate.minusWeeks(1).atStartOfDay())
                        .build(),
                OffenderKeyworker.builder() // Active after this period
                        .offenderNo(activeInFuture)
                        .staffId(TEST_STAFF_ID2)
                        .assignedDateTime(toDate.plusDays(1).atStartOfDay())
                        .expiryDateTime(null)
                        .active(true)
                        .build(),
                OffenderKeyworker.builder()  // Active for 1 day
                        .offenderNo(otherOffenderNos.get(3))
                        .staffId(TEST_STAFF_ID2)
                        .assignedDateTime(fromDate.plusDays(1).atStartOfDay())
                        .expiryDateTime(fromDate.plusDays(2).atStartOfDay())
                        .build(),
                OffenderKeyworker.builder() // Same Offender Above active 4 days later for 6 days
                        .offenderNo(otherOffenderNos.get(3))
                        .staffId(TEST_STAFF_ID2)
                        .assignedDateTime(fromDate.plusDays(6).atStartOfDay())
                        .expiryDateTime(fromDate.plusDays(12).atStartOfDay())
                        .build(),
                OffenderKeyworker.builder()  // Still active from 10 months ago
                        .offenderNo(otherOffenderNos.get(4))
                        .staffId(TEST_STAFF_ID2)
                        .assignedDateTime(toDate.minusMonths(10).atStartOfDay())
                        .expiryDateTime(null)
                        .active(true)
                        .build(),
                OffenderKeyworker.builder() // Active for last 2 weeks.
                        .offenderNo(otherOffenderNos.get(5))
                        .staffId(TEST_STAFF_ID2)
                        .assignedDateTime(toDate.minusWeeks(2).atStartOfDay())
                        .expiryDateTime(null)
                        .active(true)
                        .build()
        ));

        final List<CaseNoteUsagePrisonersDto> usageCounts = new ArrayList<>();

        usageCounts.add(CaseNoteUsagePrisonersDto.builder()
                .caseNoteSubType(KEYWORKER_CASENOTE_TYPE)
                .caseNoteSubType(KEYWORKER_SESSION_SUB_TYPE)
                .offenderNo(otherOffenderNos.get(0))
                .numCaseNotes(3)
                .build());

        usageCounts.add(CaseNoteUsagePrisonersDto.builder()
                .caseNoteSubType(KEYWORKER_CASENOTE_TYPE)
                .caseNoteSubType(KEYWORKER_ENTRY_SUB_TYPE)
                .offenderNo(otherOffenderNos.get(0))
                .numCaseNotes(3)
                .build());

        usageCounts.add(CaseNoteUsagePrisonersDto.builder()
                .caseNoteSubType(KEYWORKER_CASENOTE_TYPE)
                .caseNoteSubType(KEYWORKER_SESSION_SUB_TYPE)
                .offenderNo(otherOffenderNos.get(1))
                .numCaseNotes(1)
                .build());

        usageCounts.add(CaseNoteUsagePrisonersDto.builder()
                .caseNoteSubType(KEYWORKER_CASENOTE_TYPE)
                .caseNoteSubType(KEYWORKER_SESSION_SUB_TYPE)
                .offenderNo(otherOffenderNos.get(2))
                .numCaseNotes(1)
                .build());

        usageCounts.add(CaseNoteUsagePrisonersDto.builder()
                .caseNoteSubType(KEYWORKER_CASENOTE_TYPE)
                .caseNoteSubType(KEYWORKER_SESSION_SUB_TYPE)
                .offenderNo(otherOffenderNos.get(3))
                .numCaseNotes(1)
                .build());

        usageCounts.add(CaseNoteUsagePrisonersDto.builder()
                .caseNoteSubType(KEYWORKER_CASENOTE_TYPE)
                .caseNoteSubType(KEYWORKER_SESSION_SUB_TYPE)
                .offenderNo(otherOffenderNos.get(4))
                .numCaseNotes(4)
                .build());

        usageCounts.add(CaseNoteUsagePrisonersDto.builder()
                .caseNoteSubType(KEYWORKER_CASENOTE_TYPE)
                .caseNoteSubType(KEYWORKER_SESSION_SUB_TYPE)
                .offenderNo(otherOffenderNos.get(5))
                .numCaseNotes(2)
                .build());

        when(nomisService.getCaseNoteUsageForPrisoners( eq(otherOffenderNos), eq(TEST_STAFF_ID2), eq(KEYWORKER_CASENOTE_TYPE), isNull(String.class), eq(fromDate), eq(toDate), eq(false)))
                .thenReturn(usageCounts);

        KeyworkerStatsDto stats = service.getStatsForStaff(
                TEST_STAFF_ID2,
                TEST_AGENCY_ID,
                fromDate,
                toDate);

        assertThat(stats.getCaseNoteEntryCount()).isEqualTo(3);
        assertThat(stats.getCaseNoteSessionCount()).isEqualTo(12);
        assertThat(stats.getProjectedKeyworkerSessions()).isEqualTo(12);
        assertThat(stats.getComplianceRate()).isEqualTo(new BigDecimal("100.00"));

        verify(nomisService).getCaseNoteUsageForPrisoners(eq(otherOffenderNos), eq(TEST_STAFF_ID2), eq(KEYWORKER_CASENOTE_TYPE), isNull(String.class), eq(fromDate), eq(toDate), eq(false));
    }
}
