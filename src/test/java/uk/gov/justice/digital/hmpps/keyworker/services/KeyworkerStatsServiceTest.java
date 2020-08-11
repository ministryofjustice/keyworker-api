package uk.gov.justice.digital.hmpps.keyworker.services;

import com.microsoft.applicationinsights.TelemetryClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import uk.gov.justice.digital.hmpps.keyworker.dto.CaseNoteUsagePrisonersDto;
import uk.gov.justice.digital.hmpps.keyworker.dto.OffenderLocationDto;
import uk.gov.justice.digital.hmpps.keyworker.dto.PagingAndSortingDto;
import uk.gov.justice.digital.hmpps.keyworker.dto.Prison;
import uk.gov.justice.digital.hmpps.keyworker.dto.PrisonKeyWorkerAggregatedStats;
import uk.gov.justice.digital.hmpps.keyworker.dto.SortOrder;
import uk.gov.justice.digital.hmpps.keyworker.dto.StaffLocationRoleDto;
import uk.gov.justice.digital.hmpps.keyworker.model.AllocationType;
import uk.gov.justice.digital.hmpps.keyworker.model.Keyworker;
import uk.gov.justice.digital.hmpps.keyworker.model.KeyworkerStatus;
import uk.gov.justice.digital.hmpps.keyworker.model.OffenderKeyworker;
import uk.gov.justice.digital.hmpps.keyworker.model.PrisonKeyWorkerStatistic;
import uk.gov.justice.digital.hmpps.keyworker.repository.KeyworkerRepository;
import uk.gov.justice.digital.hmpps.keyworker.repository.OffenderKeyworkerRepository;
import uk.gov.justice.digital.hmpps.keyworker.repository.PrisonKeyWorkerStatisticRepository;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.time.temporal.ChronoUnit.DAYS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.justice.digital.hmpps.keyworker.services.KeyworkerService.KEYWORKER_CASENOTE_TYPE;
import static uk.gov.justice.digital.hmpps.keyworker.services.KeyworkerService.KEYWORKER_ENTRY_SUB_TYPE;
import static uk.gov.justice.digital.hmpps.keyworker.services.KeyworkerService.KEYWORKER_SESSION_SUB_TYPE;
import static uk.gov.justice.digital.hmpps.keyworker.services.KeyworkerService.TRANSFER_CASENOTE_TYPE;

@ExtendWith(MockitoExtension.class)
class KeyworkerStatsServiceTest {

    @Mock
    private NomisService nomisService;

    @Mock
    private OffenderKeyworkerRepository repository;

    @Mock
    private PrisonKeyWorkerStatisticRepository statisticRepository;

    @Mock
    private KeyworkerRepository keyworkerRepository;

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
    private final static List<String> offenderNos = List.of("A9876RS", "A1176RS", "A5576RS");
    private final static String inactiveOffender = "B1176RS";
    private final static String activeInFuture = "B8876RS";
    private final static List<String> otherOffenderNos = List.of("B9876RS", "B5576RS", "B5886RS", "C5576RS", "C5886RS", "C8876RS");

    @BeforeEach
    void setUp() {
        service = new KeyworkerStatsService(nomisService, prisonSupportedService, repository, statisticRepository, keyworkerRepository, telemetryClient);
        toDate = LocalDate.now();
        fromDate = toDate.minusMonths(1);
        lenient().when(prisonSupportedService.getPrisonDetail(TEST_AGENCY_ID)).thenReturn(Prison.builder().kwSessionFrequencyInWeeks(1).migrated(true).migratedDateTime(toDate.minusMonths(10).atStartOfDay()).build());
        lenient().when(prisonSupportedService.getPrisonDetail("MDI")).thenReturn(Prison.builder().kwSessionFrequencyInWeeks(2).migrated(true).migratedDateTime(toDate.minusDays(10).atStartOfDay()).build());
    }

    @Test
    void testShouldThrowIfRequiredParametersAreMissing() {
        assertThatThrownBy(() -> service.getStatsForStaff(null, null, null, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void testThatCorrectCalculationsAreMadeForAnActiveSetOfOffenders() {

        when(repository.findByStaffIdAndPrisonId(TEST_STAFF_ID, TEST_AGENCY_ID)).thenReturn(getDefaultOffenderKeyworkers());

        final var usageCounts = getCaseNoteUsagePrisonersDtos();


        when(nomisService.getCaseNoteUsageForPrisoners(offenderNos, TEST_STAFF_ID, KEYWORKER_CASENOTE_TYPE, null, fromDate, toDate, false))
                .thenReturn(usageCounts);

        final var stats = service.getStatsForStaff(
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
        return List.of(
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
    void testThatNoActiveOffendersAreNotConsideredAndLimitedOffenderAssignmentIsHandled() {

        when(repository.findByStaffIdAndPrisonId(TEST_STAFF_ID2, TEST_AGENCY_ID)).thenReturn(List.of(
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

        when(nomisService.getCaseNoteUsageForPrisoners(otherOffenderNos, TEST_STAFF_ID2, KEYWORKER_CASENOTE_TYPE, null, fromDate, toDate, false))
                .thenReturn(usageCounts);

        final var stats = service.getStatsForStaff(
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
    void testHappyPathGeneratePrisonStats() {

        basicSetup();

        lenient().when(nomisService.getCaseNoteUsageForPrisoners(eq(List.of(offenderNos.get(1), offenderNos.get(0), offenderNos.get(2))), isNull(), eq(TRANSFER_CASENOTE_TYPE),
                isNull(), eq(toDate.minusDays(1).minusMonths(6)), eq(toDate), eq(true)))
                .thenReturn(List.of(
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
        final var statsResult = service.generatePrisonStats(TEST_AGENCY_ID);

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
    void testNoTranfersGenerateNoAvgStats() {

        basicSetup();

        lenient().when(nomisService.getCaseNoteUsageForPrisoners(eq(List.of(offenderNos.get(1), offenderNos.get(0), offenderNos.get(2))), isNull(), eq(TRANSFER_CASENOTE_TYPE),
                isNull(), eq(toDate.minusDays(1).minusMonths(6)), eq(toDate), eq(true)))
                .thenReturn(Collections.emptyList());

        final var statsResult = service.generatePrisonStats(TEST_AGENCY_ID);

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
    void testPrisonStats() {
        final var prisonIds = Collections.singletonList(TEST_AGENCY_ID);

        final var now = LocalDate.now().with(TemporalAdjusters.previous(DayOfWeek.SUNDAY));
        final var fromDate = now.minusWeeks(4);
        final var toDate = now.minusDays(1);

        when(statisticRepository.getAggregatedData(eq(prisonIds), eq(fromDate), eq(toDate))).thenReturn(
                List.of(
                        new PrisonKeyWorkerAggregatedStats(
                                TEST_AGENCY_ID,
                                fromDate,
                                toDate,
                                840L,
                                400L,
                                120D,
                                600D,
                                840D,
                                3D,
                                5D)
                )
        );

        final var previousFromDate = fromDate.minusDays(DAYS.between(fromDate, toDate) + 1);
        when(statisticRepository.getAggregatedData(eq(prisonIds), eq(previousFromDate), eq(fromDate.minusDays(1)))).thenReturn(
                List.of(
                        new PrisonKeyWorkerAggregatedStats(
                                TEST_AGENCY_ID,
                                previousFromDate,
                                fromDate.minusDays(1),
                                420L,
                                400L,
                                120D,
                                600D,
                                840D,
                                4D,
                                6D)
                )
        );

        final var timeline = getTimeline(fromDate, toDate, previousFromDate, TEST_AGENCY_ID,
                400, 840, 400, 420, 840,
                600, 120);
        assertThat(timeline.size()).isEqualTo(56);

        when(statisticRepository.findByPrisonIdInAndSnapshotDateBetween(prisonIds, toDate.minusYears(1), toDate)).thenReturn(timeline);

        final var prisonStats = service.getPrisonStats(prisonIds, fromDate, toDate);

        assertThat(prisonStats.getPrisons()).hasSize(1);

        assertThat(prisonStats.getSummary().getCurrent().getDataRangeFrom()).isEqualTo(fromDate);
        assertThat(prisonStats.getSummary().getCurrent().getDataRangeTo()).isEqualTo(toDate);
        assertThat(prisonStats.getSummary().getPrevious().getDataRangeFrom()).isEqualTo(previousFromDate);
        assertThat(prisonStats.getSummary().getPrevious().getDataRangeTo()).isEqualTo(fromDate.minusDays(1));

        assertThat(prisonStats.getSummary().getCurrent().getNumProjectedKeyworkerSessions()).isEqualTo(3360);
        assertThat(prisonStats.getSummary().getCurrent().getComplianceRate()).isEqualTo(new BigDecimal("25.00"));
        assertThat(prisonStats.getSummary().getPrevious().getNumProjectedKeyworkerSessions()).isEqualTo(3360);
        assertThat(prisonStats.getSummary().getPrevious().getComplianceRate()).isEqualTo(new BigDecimal("12.50"));

        assertThat(prisonStats.getSummary().getAvgOverallKeyworkerSessions()).isEqualTo(154);
        assertThat(prisonStats.getSummary().getAvgOverallCompliance()).isEqualTo(new BigDecimal("18.34"));

        assertThat(prisonStats.getPrisons().get(TEST_AGENCY_ID).getCurrent().getDataRangeFrom()).isEqualTo(fromDate);
        assertThat(prisonStats.getPrisons().get(TEST_AGENCY_ID).getCurrent().getDataRangeTo()).isEqualTo(toDate);
        assertThat(prisonStats.getPrisons().get(TEST_AGENCY_ID).getPrevious().getDataRangeFrom()).isEqualTo(previousFromDate);
        assertThat(prisonStats.getPrisons().get(TEST_AGENCY_ID).getPrevious().getDataRangeTo()).isEqualTo(fromDate.minusDays(1));

        assertThat(prisonStats.getPrisons().get(TEST_AGENCY_ID).getCurrent().getComplianceRate()).isEqualTo(new BigDecimal("25.00"));
        assertThat(prisonStats.getPrisons().get(TEST_AGENCY_ID).getCurrent().getNumProjectedKeyworkerSessions()).isEqualTo(3360);
        assertThat(prisonStats.getPrisons().get(TEST_AGENCY_ID).getPrevious().getComplianceRate()).isEqualTo(new BigDecimal("12.50"));
        assertThat(prisonStats.getPrisons().get(TEST_AGENCY_ID).getPrevious().getNumProjectedKeyworkerSessions()).isEqualTo(3360);

        assertThat(prisonStats.getPrisons().get(TEST_AGENCY_ID).getAvgOverallCompliance()).isEqualTo(new BigDecimal("18.34"));
        assertThat(prisonStats.getPrisons().get(TEST_AGENCY_ID).getAvgOverallKeyworkerSessions()).isEqualTo(154);
    }

    @Test
    void testPrisonStatsWeekOnly() {
        final var prisonIds = Collections.singletonList("MDI");

        final var now = LocalDate.now().with(TemporalAdjusters.previous(DayOfWeek.SUNDAY));
        final var toDate = now.minusDays(1);
        final var fromDate = now.minusWeeks(2);

        when(statisticRepository.getAggregatedData(eq(prisonIds), eq(fromDate), eq(toDate))).thenReturn(
                List.of(
                        new PrisonKeyWorkerAggregatedStats(
                                "MDI",
                                fromDate,
                                toDate,
                                50L,
                                20L,
                                7D,
                                50D,
                                50D,
                                3D,
                                5D)
                )
        );

        final var previousFromDate = fromDate.minusDays(DAYS.between(fromDate, toDate) + 1);
        when(statisticRepository.getAggregatedData(eq(prisonIds), eq(previousFromDate), eq(fromDate.minusDays(1)))).thenReturn(
                List.of(
                        new PrisonKeyWorkerAggregatedStats(
                                "MDI",
                                previousFromDate,
                                fromDate.minusDays(1),
                                25L,
                                6L,
                                5D,
                                32D,
                                50D,
                                4D,
                                6D)
                )
        );

        final var timeline = getTimeline(fromDate, toDate, previousFromDate, "MDI",
                20, 50, 6, 25, 50, 50, 7);
        assertThat(timeline.size()).isEqualTo(28);

        when(statisticRepository.findByPrisonIdInAndSnapshotDateBetween(prisonIds, toDate.minusYears(1), toDate)).thenReturn(timeline);

        final var prisonStats = service.getPrisonStats(prisonIds, fromDate, toDate);

        assertThat(prisonStats.getPrisons()).hasSize(1);

        assertThat(prisonStats.getSummary().getCurrent().getDataRangeFrom()).isEqualTo(fromDate);
        assertThat(prisonStats.getSummary().getCurrent().getDataRangeTo()).isEqualTo(toDate);
        assertThat(prisonStats.getSummary().getPrevious().getDataRangeFrom()).isEqualTo(previousFromDate);
        assertThat(prisonStats.getSummary().getPrevious().getDataRangeTo()).isEqualTo(fromDate.minusDays(1));

        assertThat(prisonStats.getSummary().getCurrent().getNumProjectedKeyworkerSessions()).isEqualTo(50);
        assertThat(prisonStats.getSummary().getCurrent().getComplianceRate()).isEqualTo(new BigDecimal("100.00"));
        assertThat(prisonStats.getSummary().getPrevious().getComplianceRate()).isEqualTo(new BigDecimal("50.00"));
        assertThat(prisonStats.getSummary().getPrevious().getNumProjectedKeyworkerSessions()).isEqualTo(50);

        assertThat(prisonStats.getSummary().getAvgOverallCompliance()).isEqualTo(new BigDecimal("66.67"));
        assertThat(prisonStats.getSummary().getAvgOverallKeyworkerSessions()).isEqualTo(14);

        assertThat(prisonStats.getPrisons().get("MDI").getCurrent().getDataRangeFrom()).isEqualTo(fromDate);
        assertThat(prisonStats.getPrisons().get("MDI").getCurrent().getDataRangeTo()).isEqualTo(toDate);
        assertThat(prisonStats.getPrisons().get("MDI").getPrevious().getDataRangeFrom()).isEqualTo(previousFromDate);
        assertThat(prisonStats.getPrisons().get("MDI").getPrevious().getDataRangeTo()).isEqualTo(fromDate.minusDays(1));

        assertThat(prisonStats.getPrisons().get("MDI").getCurrent().getNumProjectedKeyworkerSessions()).isEqualTo(50);
        assertThat(prisonStats.getPrisons().get("MDI").getCurrent().getComplianceRate()).isEqualTo(new BigDecimal("100.00"));

        assertThat(prisonStats.getPrisons().get("MDI").getPrevious().getComplianceRate()).isEqualTo(new BigDecimal("50.00"));
        assertThat(prisonStats.getPrisons().get("MDI").getPrevious().getNumProjectedKeyworkerSessions()).isEqualTo(50);

        assertThat(prisonStats.getPrisons().get("MDI").getAvgOverallCompliance()).isEqualTo(new BigDecimal("66.67"));
        assertThat(prisonStats.getPrisons().get("MDI").getAvgOverallKeyworkerSessions()).isEqualTo(14);
    }

    @Test
    void testPrisonStatsSmallData() {
        final var prisonIds = Collections.singletonList("MDI");

        final var now = LocalDate.now().with(TemporalAdjusters.previous(DayOfWeek.SUNDAY));
        final var fromDate = now.minusDays(1);

        when(statisticRepository.getAggregatedData(eq(prisonIds), eq(fromDate), eq(now))).thenReturn(
                List.of(
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

        final var previousFromDate = fromDate.minusDays(DAYS.between(fromDate, now) + 1);

        final var timeline = getTimeline(fromDate, now, previousFromDate, "MDI",
                2, 5, 0, 0, 50, 50, 7);
        assertThat(timeline.size()).isEqualTo(2);

        when(statisticRepository.findByPrisonIdInAndSnapshotDateBetween(prisonIds, now.minusYears(1), now)).thenReturn(timeline);

        final var prisonStats = service.getPrisonStats(prisonIds, fromDate, now);

        assertThat(prisonStats.getPrisons()).hasSize(1);

        assertThat(prisonStats.getSummary().getCurrent().getDataRangeFrom()).isEqualTo(fromDate);
        assertThat(prisonStats.getSummary().getCurrent().getDataRangeTo()).isEqualTo(now);
        assertThat(prisonStats.getSummary().getPrevious()).isNull();

        assertThat(prisonStats.getSummary().getCurrent().getNumberKeyWorkerSessions()).isEqualTo(5);
        assertThat(prisonStats.getSummary().getCurrent().getPercentagePrisonersWithKeyworker()).isEqualTo(new BigDecimal("100.00"));
        assertThat(prisonStats.getSummary().getCurrent().getNumProjectedKeyworkerSessions()).isEqualTo(7);
        assertThat(prisonStats.getSummary().getCurrent().getComplianceRate()).isEqualTo(new BigDecimal("71.43"));

        assertThat(prisonStats.getSummary().getAvgOverallCompliance()).isEqualTo(new BigDecimal("66.67"));
        assertThat(prisonStats.getSummary().getAvgOverallKeyworkerSessions()).isEqualTo(2);

        assertThat(prisonStats.getPrisons().get("MDI").getCurrent().getDataRangeFrom()).isEqualTo(fromDate);
        assertThat(prisonStats.getPrisons().get("MDI").getCurrent().getDataRangeTo()).isEqualTo(now);
        assertThat(prisonStats.getPrisons().get("MDI").getPrevious()).isNull();

        assertThat(prisonStats.getPrisons().get("MDI").getCurrent().getNumProjectedKeyworkerSessions()).isEqualTo(7);
        assertThat(prisonStats.getPrisons().get("MDI").getCurrent().getComplianceRate()).isEqualTo(new BigDecimal("71.43"));

        assertThat(prisonStats.getPrisons().get("MDI").getAvgOverallCompliance()).isEqualTo(new BigDecimal("66.67"));
        assertThat(prisonStats.getPrisons().get("MDI").getAvgOverallKeyworkerSessions()).isEqualTo(2);
    }

    private List<PrisonKeyWorkerStatistic> getTimeline(final LocalDate fromDate, final LocalDate toDate, final LocalDate previousFromDate, final String prisonId, final int kwEntriesCurrent, final int kwSessionsCurrent, final int kwEntriesPrevious, final int kwSessionsPrevious, final int totalNumPrisoners, final int numPrisonersAssignedKeyWorker, final int numberOfActiveKeyworkers) {
        final List<PrisonKeyWorkerStatistic> timeline = new ArrayList<>();

        var day = 0;
        double between = DAYS.between(fromDate, toDate) + 1;
        while (day <= DAYS.between(fromDate, toDate)) {
            timeline.add(PrisonKeyWorkerStatistic.builder()
                    .prisonId(prisonId)
                    .snapshotDate(fromDate.plusDays(day))
                    .totalNumPrisoners(totalNumPrisoners)
                    .numPrisonersAssignedKeyWorker(numPrisonersAssignedKeyWorker)
                    .numberOfActiveKeyworkers(numberOfActiveKeyworkers)
                    .numberKeyWorkerEntries((int) (kwEntriesCurrent / between))
                    .numberKeyWorkerSessions((int) (kwSessionsCurrent / between))
                    .avgNumDaysFromReceptionToKeyWorkingSession(5)
                    .avgNumDaysFromReceptionToAllocationDays(3)
                    .build());
            day++;
        }

        if (kwSessionsPrevious > 0) {
            day = 0;
            between = DAYS.between(previousFromDate, fromDate) + 1;
            while (day < DAYS.between(previousFromDate, fromDate)) {
                timeline.add(PrisonKeyWorkerStatistic.builder()
                        .prisonId(prisonId)
                        .snapshotDate(previousFromDate.plusDays(day))
                        .totalNumPrisoners(totalNumPrisoners)
                        .numPrisonersAssignedKeyWorker(numPrisonersAssignedKeyWorker)
                        .numberOfActiveKeyworkers(numberOfActiveKeyworkers)
                        .numberKeyWorkerEntries((int) (kwEntriesPrevious / between))
                        .numberKeyWorkerSessions((int) (kwSessionsPrevious / between))
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

        final var offenderLocations = offenderNos.stream().map(offenderNo ->
                OffenderLocationDto.builder()
                        .agencyId(TEST_AGENCY_ID)
                        .offenderNo(offenderNo)
                        .build()).collect(Collectors.toList());

        when(nomisService.getOffendersAtLocation(eq(TEST_AGENCY_ID), isA(String.class), isA(SortOrder.class), eq(true)))
                .thenReturn(offenderLocations);

        when(repository.findByActiveAndPrisonIdAndOffenderNoInAndAllocationTypeIsNot(eq(true), eq(TEST_AGENCY_ID), eq(offenderNos), eq(AllocationType.PROVISIONAL)))
                .thenReturn(getDefaultOffenderKeyworkers());

        final var staffLocationRoleDtos = List.of(
                StaffLocationRoleDto.builder()
                        .agencyId(TEST_AGENCY_ID)
                        .staffId(-5L)
                        .build(),
                StaffLocationRoleDto.builder()
                        .agencyId(TEST_AGENCY_ID)
                        .staffId(-4L)
                        .build(),
                StaffLocationRoleDto.builder()
                        .agencyId(TEST_AGENCY_ID)
                        .staffId(-3L)
                        .build()
        );
        when(nomisService.getActiveStaffKeyWorkersForPrison(eq(TEST_AGENCY_ID), eq(Optional.empty()), isA(PagingAndSortingDto.class), eq(true)))
                .thenReturn(new ResponseEntity<>(staffLocationRoleDtos, HttpStatus.OK));

        lenient().when(keyworkerRepository.findById(-5L)).thenReturn(Optional.of(Keyworker.builder().staffId(-5L).status(KeyworkerStatus.ACTIVE).build()));
        lenient().when(keyworkerRepository.findById(-3L)).thenReturn(Optional.of(Keyworker.builder().staffId(-5L).status(KeyworkerStatus.INACTIVE).build()));

        when(nomisService.getCaseNoteUsageByPrison(eq(TEST_AGENCY_ID),
                eq(KEYWORKER_CASENOTE_TYPE), isNull(), eq(toDate.minusDays(1)),
                eq(toDate.minusDays(1)), eq(true)))
                .thenReturn(getCaseNoteUsagePrisonersDtos());

        final var assignedOffenders = List.of(
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
                eq(new HashSet<>(List.of(offenderNos.get(2), offenderNos.get(1)))), eq(AllocationType.PROVISIONAL)))
                .thenReturn(assignedOffenders.subList(0, 1));
    }

    private void verifyChecks() {
        verify(statisticRepository).findOneByPrisonIdAndSnapshotDate(TEST_AGENCY_ID, toDate.minusDays(1));
        verify(nomisService).getOffendersAtLocation(eq(TEST_AGENCY_ID), isA(String.class), isA(SortOrder.class), eq(true));
        verify(repository).findByActiveAndPrisonIdAndOffenderNoInAndAllocationTypeIsNot(eq(true), eq(TEST_AGENCY_ID), eq(offenderNos), eq(AllocationType.PROVISIONAL));
        verify(nomisService).getActiveStaffKeyWorkersForPrison(eq(TEST_AGENCY_ID), eq(Optional.empty()), isA(PagingAndSortingDto.class), eq(true));
        verify(nomisService).getCaseNoteUsageByPrison(eq(TEST_AGENCY_ID),
                eq(KEYWORKER_CASENOTE_TYPE), isNull(), eq(toDate.minusDays(1)),
                eq(toDate.minusDays(1)), eq(true));
        verify(repository).findByPrisonIdAndAssignedDateTimeBetween(eq(TEST_AGENCY_ID), eq(toDate.atStartOfDay().minusDays(1)), eq(toDate.atStartOfDay()));
        verify(repository).findByPrisonIdAndAssignedDateTimeBeforeAndOffenderNoInAndAllocationTypeIsNot(eq(TEST_AGENCY_ID), eq(toDate.minusDays(1).atStartOfDay()),
                eq(new HashSet<>(List.of(offenderNos.get(2), offenderNos.get(1)))), eq(AllocationType.PROVISIONAL));
        verify(nomisService).getCaseNoteUsageForPrisoners(eq(List.of(offenderNos.get(1), offenderNos.get(0), offenderNos.get(2))), isNull(), eq(TRANSFER_CASENOTE_TYPE),
                isNull(), eq(toDate.minusDays(1).minusMonths(6)), eq(toDate), eq(true));
    }

}
