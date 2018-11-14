package uk.gov.justice.digital.hmpps.keyworker.services;

import com.microsoft.applicationinsights.TelemetryClient;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import uk.gov.justice.digital.hmpps.keyworker.dto.*;
import uk.gov.justice.digital.hmpps.keyworker.model.AllocationType;
import uk.gov.justice.digital.hmpps.keyworker.model.OffenderKeyworker;
import uk.gov.justice.digital.hmpps.keyworker.model.PrisonKeyWorkerStatistic;
import uk.gov.justice.digital.hmpps.keyworker.repository.OffenderKeyworkerRepository;
import uk.gov.justice.digital.hmpps.keyworker.repository.PrisonKeyWorkerStatisticRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

import static java.time.temporal.ChronoUnit.DAYS;
import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.mockito.Matchers.*;
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
        MockitoAnnotations.initMocks(this);
        service = new KeyworkerStatsService(nomisService, prisonSupportedService, repository, statisticRepository, telemetryClient);
        toDate = LocalDate.now();
        fromDate = toDate.minusMonths(1);
        when(prisonSupportedService.getPrisonDetail(TEST_AGENCY_ID)).thenReturn(Prison.builder().kwSessionFrequencyInWeeks(1).migrated(true).migratedDateTime(toDate.minusMonths(10).atStartOfDay()).build());
        when(prisonSupportedService.getPrisonDetail("MDI")).thenReturn(Prison.builder().kwSessionFrequencyInWeeks(2).migrated(true).migratedDateTime(toDate.minusDays(10).atStartOfDay()).build());
    }

    @Test(expected = NullPointerException.class)
    public void testShouldThrowIfRequiredParametersAreMissing() {
        service.getStatsForStaff(null, null, null, null);
    }


    @Test
    public void testThatCorrectCalculationsAreMadeForAnActiveSetOfOffenders() {

        when(repository.findByStaffIdAndPrisonId(TEST_STAFF_ID, TEST_AGENCY_ID)).thenReturn(getDefaultOffenderKeyworkers());

        final List<CaseNoteUsagePrisonersDto> usageCounts = getCaseNoteUsagePrisonersDtos();


        when(nomisService.getCaseNoteUsageForPrisoners( offenderNos, TEST_STAFF_ID, KEYWORKER_CASENOTE_TYPE, null, fromDate, toDate, false))
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

        verify(nomisService).getCaseNoteUsageForPrisoners(offenderNos, TEST_STAFF_ID, KEYWORKER_CASENOTE_TYPE,
                null, fromDate, toDate, false);
    }

    private List<CaseNoteUsagePrisonersDto> getCaseNoteUsagePrisonersDtos() {
        final List<CaseNoteUsagePrisonersDto> usageCounts = new ArrayList<>();

        usageCounts.add(CaseNoteUsagePrisonersDto.builder()
                .caseNoteSubType(KEYWORKER_CASENOTE_TYPE)
                .caseNoteSubType(KEYWORKER_SESSION_SUB_TYPE)
                .offenderNo(offenderNos.get(0))
                .latestCaseNote(toDate)
                .numCaseNotes(2)
                .build());

        usageCounts.add(CaseNoteUsagePrisonersDto.builder()
                .caseNoteSubType(KEYWORKER_CASENOTE_TYPE)
                .caseNoteSubType(KEYWORKER_ENTRY_SUB_TYPE)
                .offenderNo(offenderNos.get(0))
                .numCaseNotes(3)
                .latestCaseNote(toDate)
                .build());

        usageCounts.add(CaseNoteUsagePrisonersDto.builder()
                .caseNoteSubType(KEYWORKER_CASENOTE_TYPE)
                .caseNoteSubType(KEYWORKER_SESSION_SUB_TYPE)
                .offenderNo(offenderNos.get(1))
                .numCaseNotes(1)
                .latestCaseNote(toDate)
                .build());

        usageCounts.add(CaseNoteUsagePrisonersDto.builder()
                .caseNoteSubType(KEYWORKER_CASENOTE_TYPE)
                .caseNoteSubType(KEYWORKER_ENTRY_SUB_TYPE)
                .offenderNo(offenderNos.get(1))
                .numCaseNotes(2)
                .latestCaseNote(toDate)
                .build());

        usageCounts.add(CaseNoteUsagePrisonersDto.builder()
                .caseNoteSubType(KEYWORKER_CASENOTE_TYPE)
                .caseNoteSubType(KEYWORKER_SESSION_SUB_TYPE)
                .offenderNo(offenderNos.get(2))
                .numCaseNotes(1)
                .latestCaseNote(toDate)
                .build());
        return usageCounts;
    }

    private List<OffenderKeyworker> getDefaultOffenderKeyworkers() {
        return Arrays.asList(
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
        );
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

        when(nomisService.getCaseNoteUsageForPrisoners( otherOffenderNos, TEST_STAFF_ID2, KEYWORKER_CASENOTE_TYPE, null, fromDate, toDate, false))
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

        verify(nomisService).getCaseNoteUsageForPrisoners(otherOffenderNos, TEST_STAFF_ID2, KEYWORKER_CASENOTE_TYPE,
                null, fromDate, toDate, false);
    }

    @Test
    public void testHappyPathGeneratePrisonStats() {

        basicSetup();

        when(nomisService.getCaseNoteUsageForPrisoners(eq(Arrays.asList(offenderNos.get(1), offenderNos.get(0), offenderNos.get(2))), isNull(Long.class), eq(TRANSFER_CASENOTE_TYPE),
                isNull(String.class), eq(toDate.minusDays(1).minusMonths(6)), eq(toDate), eq(true)))
                .thenReturn(Arrays.asList(
                        CaseNoteUsagePrisonersDto.builder()
                                .caseNoteSubType(TRANSFER_CASENOTE_TYPE)
                                .caseNoteSubType("IN")
                                .offenderNo(offenderNos.get(2))
                                .latestCaseNote(toDate.minusDays(2))
                                .numCaseNotes(1)
                                .build(),
                        CaseNoteUsagePrisonersDto.builder()
                                .caseNoteSubType(TRANSFER_CASENOTE_TYPE)
                                .caseNoteSubType("IN")
                                .offenderNo(offenderNos.get(1))
                                .latestCaseNote(toDate.minusDays(3))
                                .numCaseNotes(1)
                                .build())
                );
        PrisonKeyWorkerStatistic statsResult = service.generatePrisonStats(TEST_AGENCY_ID);

        assertThat(statsResult.getTotalNumPrisoners()).isEqualTo(3);
        assertThat(statsResult.getNumberKeyWorkerSessions()).isEqualTo(4);
        assertThat(statsResult.getNumberKeyWorkerEntries()).isEqualTo(5);
        assertThat(statsResult.getNumPrisonersAssignedKeyWorker()).isEqualTo(3);
        assertThat(statsResult.getNumberOfActiveKeyworkers()).isEqualTo(2);
        assertThat(statsResult.getAvgNumDaysFromReceptionToAllocationDays()).isEqualTo(3);
        assertThat(statsResult.getAvgNumDaysFromReceptionToKeyWorkingSession()).isEqualTo(3);

        verifyChecks();

    }

    @Test
    public void testNoTranfersGenerateNoAvgStats() {

        basicSetup();

        when(nomisService.getCaseNoteUsageForPrisoners(eq(Arrays.asList(offenderNos.get(1), offenderNos.get(0), offenderNos.get(2))), isNull(Long.class), eq(TRANSFER_CASENOTE_TYPE),
                isNull(String.class), eq(toDate.minusDays(1).minusMonths(6)), eq(toDate), eq(true)))
                .thenReturn(Collections.emptyList());

        PrisonKeyWorkerStatistic statsResult = service.generatePrisonStats(TEST_AGENCY_ID);

        assertThat(statsResult.getTotalNumPrisoners()).isEqualTo(3);
        assertThat(statsResult.getNumberKeyWorkerSessions()).isEqualTo(4);
        assertThat(statsResult.getNumberKeyWorkerEntries()).isEqualTo(5);
        assertThat(statsResult.getNumPrisonersAssignedKeyWorker()).isEqualTo(3);
        assertThat(statsResult.getNumberOfActiveKeyworkers()).isEqualTo(2);
        assertThat(statsResult.getAvgNumDaysFromReceptionToAllocationDays()).isNull();
        assertThat(statsResult.getAvgNumDaysFromReceptionToKeyWorkingSession()).isNull();

        verifyChecks();
    }

    @Test
    public void testPrisonStats() {
        List<String> prisonIds = Collections.singletonList(TEST_AGENCY_ID);

        LocalDate now = LocalDate.now();
        LocalDate fromDate = now.minusMonths(1);
        LocalDate toDate = now;

        when(statisticRepository.getAggregatedData(eq(prisonIds), eq(fromDate), eq(toDate))).thenReturn(
                Arrays.asList(
                        new PrisonKeyWorkerAggregatedStats(
                                TEST_AGENCY_ID,
                                fromDate,
                                now,
                                229L,
                                50L,
                                7D,
                                50D,
                                50D,
                                3D,
                                5D)
                        )
                );

        LocalDate previousFromDate = fromDate.minusDays(DAYS.between(fromDate, toDate)+1);
        when(statisticRepository.getAggregatedData(eq(prisonIds), eq(previousFromDate), eq(fromDate.minusDays(1)))).thenReturn(
                Arrays.asList(
                        new PrisonKeyWorkerAggregatedStats(
                                TEST_AGENCY_ID,
                                previousFromDate,
                                fromDate.minusDays(1),
                                72L,
                                25L,
                                5D,
                                32D,
                                40D,
                                4D,
                                6D)
                )
        );

        List<PrisonKeyWorkerStatistic> timeline = getTimeline(fromDate, toDate, previousFromDate, TEST_AGENCY_ID,
                50, 229, 25, 72);
        assertThat(timeline.size()).isEqualTo(63);

        when(statisticRepository.findByPrisonIdInAndSnapshotDateBetween(prisonIds, toDate.minusYears(1), toDate)).thenReturn(timeline);

        KeyworkerStatSummary prisonStats = service.getPrisonStats(prisonIds, null, null);

        assertThat(prisonStats.getPrisons()).hasSize(1);

        assertThat(prisonStats.getSummary().getCurrent().getDataRangeFrom()).isEqualTo(fromDate);
        assertThat(prisonStats.getSummary().getCurrent().getDataRangeTo()).isEqualTo(now);
        assertThat(prisonStats.getSummary().getPrevious().getDataRangeFrom()).isEqualTo(previousFromDate);
        assertThat(prisonStats.getSummary().getPrevious().getDataRangeTo()).isEqualTo(fromDate.minusDays(1));

        assertThat(prisonStats.getSummary().getCurrent().getNumProjectedKeyworkerSessions()).isEqualTo(229);
        assertThat(prisonStats.getSummary().getCurrent().getComplianceRate()).isEqualTo(new BigDecimal("100.00"));
        assertThat(prisonStats.getSummary().getPrevious().getNumProjectedKeyworkerSessions()).isEqualTo(146);
        assertThat(prisonStats.getSummary().getPrevious().getComplianceRate()).isEqualTo(new BigDecimal("49.32"));

        assertThat(prisonStats.getSummary().getAvgOverallCompliance()).isEqualTo(new BigDecimal("75.71"));
        assertThat(prisonStats.getSummary().getAvgOverallKeyworkerSessions()).isEqualTo(28);

        assertThat(prisonStats.getPrisons().get(TEST_AGENCY_ID).getCurrent().getDataRangeFrom()).isEqualTo(fromDate);
        assertThat(prisonStats.getPrisons().get(TEST_AGENCY_ID).getCurrent().getDataRangeTo()).isEqualTo(now);
        assertThat(prisonStats.getPrisons().get(TEST_AGENCY_ID).getPrevious().getDataRangeFrom()).isEqualTo(previousFromDate);
        assertThat(prisonStats.getPrisons().get(TEST_AGENCY_ID).getPrevious().getDataRangeTo()).isEqualTo(fromDate.minusDays(1));

        assertThat(prisonStats.getPrisons().get(TEST_AGENCY_ID).getCurrent().getComplianceRate()).isEqualTo(new BigDecimal("100.00"));
        assertThat(prisonStats.getPrisons().get(TEST_AGENCY_ID).getCurrent().getNumProjectedKeyworkerSessions()).isEqualTo(229);
        assertThat(prisonStats.getPrisons().get(TEST_AGENCY_ID).getPrevious().getComplianceRate()).isEqualTo(new BigDecimal("49.32"));
        assertThat(prisonStats.getPrisons().get(TEST_AGENCY_ID).getPrevious().getNumProjectedKeyworkerSessions()).isEqualTo(146);

        assertThat(prisonStats.getPrisons().get(TEST_AGENCY_ID).getAvgOverallCompliance()).isEqualTo(new BigDecimal("75.71"));
        assertThat(prisonStats.getPrisons().get(TEST_AGENCY_ID).getAvgOverallKeyworkerSessions()).isEqualTo(28);
    }

    @Test
    public void testPrisonStatsWeekOnly() {
        List<String> prisonIds = Collections.singletonList("MDI");

        LocalDate now = LocalDate.now();
        LocalDate fromDate = now.minusWeeks(2);

        when(statisticRepository.getAggregatedData(eq(prisonIds), eq(fromDate), eq(now))).thenReturn(
                Arrays.asList(
                        new PrisonKeyWorkerAggregatedStats(
                                "MDI",
                                fromDate,
                                now,
                                54L,
                                20L,
                                7D,
                                50D,
                                50D,
                                3D,
                                5D)
                )
        );

        LocalDate previousFromDate = fromDate.minusDays(DAYS.between(fromDate, now)+1);
        when(statisticRepository.getAggregatedData(eq(prisonIds), eq(previousFromDate), eq(fromDate.minusDays(1)))).thenReturn(
                Arrays.asList(
                        new PrisonKeyWorkerAggregatedStats(
                                "MDI",
                                previousFromDate,
                                fromDate.minusDays(1),
                                15L,
                                6L,
                                5D,
                                32D,
                                40D,
                                4D,
                                6D)
                )
        );

        List<PrisonKeyWorkerStatistic> timeline = getTimeline(fromDate, now, previousFromDate, "MDI",
                20, 54, 6, 15);
        assertThat(timeline.size()).isEqualTo(29);

        when(statisticRepository.findByPrisonIdInAndSnapshotDateBetween(prisonIds, now.minusYears(1), now)).thenReturn(timeline);

        KeyworkerStatSummary prisonStats = service.getPrisonStats(prisonIds, fromDate, now);

        assertThat(prisonStats.getPrisons()).hasSize(1);

        assertThat(prisonStats.getSummary().getCurrent().getDataRangeFrom()).isEqualTo(fromDate);
        assertThat(prisonStats.getSummary().getCurrent().getDataRangeTo()).isEqualTo(now);
        assertThat(prisonStats.getSummary().getPrevious().getDataRangeFrom()).isEqualTo(previousFromDate);
        assertThat(prisonStats.getSummary().getPrevious().getDataRangeTo()).isEqualTo(fromDate.minusDays(1));

        assertThat(prisonStats.getSummary().getCurrent().getNumProjectedKeyworkerSessions()).isEqualTo(54);
        assertThat(prisonStats.getSummary().getCurrent().getComplianceRate()).isEqualTo(new BigDecimal("100.00"));
        assertThat(prisonStats.getSummary().getPrevious().getNumProjectedKeyworkerSessions()).isEqualTo(34);
        assertThat(prisonStats.getSummary().getPrevious().getComplianceRate()).isEqualTo(new BigDecimal("44.12"));

        assertThat(prisonStats.getSummary().getAvgOverallCompliance()).isEqualTo(new BigDecimal("77.14"));
        assertThat(prisonStats.getSummary().getAvgOverallKeyworkerSessions()).isEqualTo(11);

        assertThat(prisonStats.getPrisons().get("MDI").getCurrent().getDataRangeFrom()).isEqualTo(fromDate);
        assertThat(prisonStats.getPrisons().get("MDI").getCurrent().getDataRangeTo()).isEqualTo(now);
        assertThat(prisonStats.getPrisons().get("MDI").getPrevious().getDataRangeFrom()).isEqualTo(previousFromDate);
        assertThat(prisonStats.getPrisons().get("MDI").getPrevious().getDataRangeTo()).isEqualTo(fromDate.minusDays(1));

        assertThat(prisonStats.getPrisons().get("MDI").getCurrent().getNumProjectedKeyworkerSessions()).isEqualTo(54);
        assertThat(prisonStats.getPrisons().get("MDI").getCurrent().getComplianceRate()).isEqualTo(new BigDecimal("100.00"));

        assertThat(prisonStats.getPrisons().get("MDI").getPrevious().getComplianceRate()).isEqualTo(new BigDecimal("44.12"));
        assertThat(prisonStats.getPrisons().get("MDI").getPrevious().getNumProjectedKeyworkerSessions()).isEqualTo(34);

        assertThat(prisonStats.getPrisons().get("MDI").getAvgOverallCompliance()).isEqualTo(new BigDecimal("77.14"));
        assertThat(prisonStats.getPrisons().get("MDI").getAvgOverallKeyworkerSessions()).isEqualTo(11);
    }

    @Test
    public void testPrisonStatsSmallData() {
        List<String> prisonIds = Collections.singletonList("MDI");

        LocalDate now = LocalDate.now();
        LocalDate fromDate = now.minusDays(1);

        when(statisticRepository.getAggregatedData(eq(prisonIds), eq(fromDate), eq(now))).thenReturn(
                Arrays.asList(
                        new PrisonKeyWorkerAggregatedStats(
                                "MDI",
                                fromDate,
                                now,
                                5L,
                                2L,
                                7D,
                                50D,
                                50D,
                                3D,
                                5D)
                )
        );

        LocalDate previousFromDate = fromDate.minusDays(DAYS.between(fromDate, now)+1);
        when(statisticRepository.getAggregatedData(eq(prisonIds), eq(previousFromDate), eq(fromDate))).thenReturn(new ArrayList<>());

        List<PrisonKeyWorkerStatistic> timeline = getTimeline(fromDate, now, previousFromDate, "MDI",
                2, 5, 0, 0);
        assertThat(timeline.size()).isEqualTo(1);

        when(statisticRepository.findByPrisonIdInAndSnapshotDateBetween(prisonIds, now.minusYears(1), now)).thenReturn(timeline);

        KeyworkerStatSummary prisonStats = service.getPrisonStats(prisonIds, fromDate, now);

        assertThat(prisonStats.getPrisons()).hasSize(1);

        assertThat(prisonStats.getSummary().getCurrent().getDataRangeFrom()).isEqualTo(fromDate);
        assertThat(prisonStats.getSummary().getCurrent().getDataRangeTo()).isEqualTo(now);
        assertThat(prisonStats.getSummary().getPrevious()).isNull();

        assertThat(prisonStats.getSummary().getCurrent().getNumProjectedKeyworkerSessions()).isEqualTo(7);
        assertThat(prisonStats.getSummary().getCurrent().getComplianceRate()).isEqualTo(new BigDecimal("71.43"));

        assertThat(prisonStats.getSummary().getAvgOverallCompliance()).isEqualTo(new BigDecimal("166.67"));
        assertThat(prisonStats.getSummary().getAvgOverallKeyworkerSessions()).isEqualTo(5);

        assertThat(prisonStats.getPrisons().get("MDI").getCurrent().getDataRangeFrom()).isEqualTo(fromDate);
        assertThat(prisonStats.getPrisons().get("MDI").getCurrent().getDataRangeTo()).isEqualTo(now);
        assertThat(prisonStats.getPrisons().get("MDI").getPrevious()).isNull();

        assertThat(prisonStats.getPrisons().get("MDI").getCurrent().getNumProjectedKeyworkerSessions()).isEqualTo(7);
        assertThat(prisonStats.getPrisons().get("MDI").getCurrent().getComplianceRate()).isEqualTo(new BigDecimal("71.43"));

        assertThat(prisonStats.getPrisons().get("MDI").getAvgOverallCompliance()).isEqualTo(new BigDecimal("166.67"));
        assertThat(prisonStats.getPrisons().get("MDI").getAvgOverallKeyworkerSessions()).isEqualTo(5);
    }

    private List<PrisonKeyWorkerStatistic> getTimeline(LocalDate fromDate, LocalDate toDate, LocalDate previousFromDate, String prisonId, int kwEntriesCurrent, int kwSessionsCurrent, int kwEntriesPrevious, int kwSessionsPrevious) {
        List<PrisonKeyWorkerStatistic> timeline = new ArrayList<>();

        int day = 0;
        long between = DAYS.between(fromDate, toDate);
        while (day < between) {
            timeline.add(PrisonKeyWorkerStatistic.builder()
                    .prisonId(prisonId)
                    .snapshotDate(fromDate.plusDays(day))
                    .totalNumPrisoners(50)
                    .numPrisonersAssignedKeyWorker(50)
                    .numberOfActiveKeyworkers(7)
                    .numberKeyWorkerEntries((int)(kwEntriesCurrent / (double)between))
                    .numberKeyWorkerSessions((int)(kwSessionsCurrent / (double)between))
                    .avgNumDaysFromReceptionToKeyWorkingSession(5)
                    .avgNumDaysFromReceptionToAllocationDays(3)
                    .build());
            day++;
        }

        if (kwSessionsPrevious > 0) {
            day = 0;
            between = DAYS.between(previousFromDate, fromDate);
            while (day < between) {
                timeline.add(PrisonKeyWorkerStatistic.builder()
                        .prisonId(prisonId)
                        .snapshotDate(previousFromDate.plusDays(day))
                        .totalNumPrisoners(40)
                        .numPrisonersAssignedKeyWorker(32)
                        .numberOfActiveKeyworkers(5)
                        .numberKeyWorkerEntries((int) (kwEntriesPrevious / (double) between))
                        .numberKeyWorkerSessions((int) (kwSessionsPrevious / (double) between))
                        .avgNumDaysFromReceptionToKeyWorkingSession(6)
                        .avgNumDaysFromReceptionToAllocationDays(4)
                        .build());
                day++;
            }
        }
        return timeline.stream().sorted(Comparator.comparing(PrisonKeyWorkerStatistic::getSnapshotDate)).collect(Collectors.toList());
    }

    private void basicSetup() {
        when(statisticRepository.findOneByPrisonIdAndSnapshotDate(TEST_AGENCY_ID, toDate.minusDays(1)))
                .thenReturn(null);

        List<OffenderLocationDto> offenderLocations = offenderNos.stream().map(offenderNo ->
                OffenderLocationDto.builder()
                        .agencyId(TEST_AGENCY_ID)
                        .offenderNo(offenderNo)
                        .build()).collect(Collectors.toList());

        when(nomisService.getOffendersAtLocation(eq(TEST_AGENCY_ID), isA(String.class), isA(SortOrder.class), eq(true)))
                .thenReturn(offenderLocations);

        when(repository.findByActiveAndPrisonIdAndOffenderNoInAndAllocationTypeIsNot(eq(true), eq(TEST_AGENCY_ID), eq(offenderNos), eq(AllocationType.PROVISIONAL)))
                .thenReturn(getDefaultOffenderKeyworkers());

        List<StaffLocationRoleDto> staffLocationRoleDtos = Arrays.asList(
                StaffLocationRoleDto.builder()
                        .agencyId(TEST_AGENCY_ID)
                        .staffId(-5L)
                        .build(),
                StaffLocationRoleDto.builder()
                        .agencyId(TEST_AGENCY_ID)
                        .staffId(-4L)
                        .build()
        );
        when(nomisService.getActiveStaffKeyWorkersForPrison(eq(TEST_AGENCY_ID), eq(Optional.empty()), isA(PagingAndSortingDto.class), eq(true)))
                .thenReturn(new ResponseEntity<>(staffLocationRoleDtos, HttpStatus.OK));


        when(nomisService.getCaseNoteUsageForPrisoners(eq(offenderNos), isNull(Long.class),
                eq(KEYWORKER_CASENOTE_TYPE), isNull(String.class), eq(toDate.minusDays(1)),
                eq(toDate.minusDays(1)), eq(true)))
                .thenReturn(getCaseNoteUsagePrisonersDtos());

        List<OffenderKeyworker> assignedOffenders = Arrays.asList(
                OffenderKeyworker.builder()
                        .offenderNo(offenderNos.get(2))
                        .staffId(TEST_STAFF_ID)
                        .assignedDateTime(toDate.atStartOfDay())
                        .expiryDateTime(null)
                        .build(),
                OffenderKeyworker.builder()
                        .offenderNo(offenderNos.get(1))
                        .staffId(TEST_STAFF_ID)
                        .assignedDateTime(toDate.atStartOfDay())
                        .build());
        when(repository.findByPrisonIdAndAssignedDateTimeBetween(eq(TEST_AGENCY_ID), eq(toDate.atStartOfDay().minusDays(1)), eq(toDate.atStartOfDay())))
                .thenReturn(assignedOffenders);

        when(repository.findByPrisonIdAndAssignedDateTimeBeforeAndOffenderNoInAndAllocationTypeIsNot(eq(TEST_AGENCY_ID), eq(toDate.minusDays(1).atStartOfDay()),
                eq(new HashSet<>(Arrays.asList(offenderNos.get(2), offenderNos.get(1)))), eq(AllocationType.PROVISIONAL)))
                .thenReturn(assignedOffenders.subList(0, 1));
    }

    private void verifyChecks() {
        verify(statisticRepository).findOneByPrisonIdAndSnapshotDate(TEST_AGENCY_ID, toDate.minusDays(1));
        verify(nomisService).getOffendersAtLocation(eq(TEST_AGENCY_ID), isA(String.class), isA(SortOrder.class), eq(true));
        verify(repository).findByActiveAndPrisonIdAndOffenderNoInAndAllocationTypeIsNot(eq(true), eq(TEST_AGENCY_ID), eq(offenderNos), eq(AllocationType.PROVISIONAL));
        verify(nomisService).getActiveStaffKeyWorkersForPrison(eq(TEST_AGENCY_ID), eq(Optional.empty()), isA(PagingAndSortingDto.class), eq(true));
        verify(nomisService).getCaseNoteUsageForPrisoners(eq(offenderNos), isNull(Long.class),
                eq(KEYWORKER_CASENOTE_TYPE), isNull(String.class), eq(toDate.minusDays(1)),
                eq(toDate.minusDays(1)), eq(true));
        verify(repository).findByPrisonIdAndAssignedDateTimeBetween(eq(TEST_AGENCY_ID), eq(toDate.atStartOfDay().minusDays(1)), eq(toDate.atStartOfDay()));
        verify(repository).findByPrisonIdAndAssignedDateTimeBeforeAndOffenderNoInAndAllocationTypeIsNot(eq(TEST_AGENCY_ID), eq(toDate.minusDays(1).atStartOfDay()),
                eq(new HashSet<>(Arrays.asList(offenderNos.get(2), offenderNos.get(1)))), eq(AllocationType.PROVISIONAL));
        verify(nomisService).getCaseNoteUsageForPrisoners(eq(Arrays.asList(offenderNos.get(1), offenderNos.get(0), offenderNos.get(2))), isNull(Long.class), eq(TRANSFER_CASENOTE_TYPE),
                isNull(String.class), eq(toDate.minusDays(1).minusMonths(6)), eq(toDate), eq(true));
    }

}
