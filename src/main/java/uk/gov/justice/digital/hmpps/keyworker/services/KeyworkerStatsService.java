package uk.gov.justice.digital.hmpps.keyworker.services;

import lombok.Getter;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.digital.hmpps.keyworker.dto.*;
import uk.gov.justice.digital.hmpps.keyworker.model.OffenderKeyworker;
import uk.gov.justice.digital.hmpps.keyworker.model.PrisonKeyWorkerStatistic;
import uk.gov.justice.digital.hmpps.keyworker.repository.OffenderKeyworkerRepository;
import uk.gov.justice.digital.hmpps.keyworker.repository.PrisonKeyWorkerStatisticRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.LongSummaryStatistics;
import java.util.Map;
import java.util.stream.Collectors;

import static java.time.temporal.ChronoUnit.DAYS;
import static java.time.temporal.ChronoUnit.WEEKS;
import static uk.gov.justice.digital.hmpps.keyworker.services.KeyworkerService.*;

@Service
@Transactional(readOnly = true)
public class KeyworkerStatsService {

    private final NomisService nomisService;
    private final OffenderKeyworkerRepository repository;
    private final PrisonKeyWorkerStatisticRepository statisticRepository;
    private final PrisonSupportedService prisonSupportedService;

    private final BigDecimal HUNDRED = new BigDecimal("100.00");

    public KeyworkerStatsService(NomisService nomisService, PrisonSupportedService prisonSupportedService,
                                 OffenderKeyworkerRepository repository,
                                 PrisonKeyWorkerStatisticRepository statisticRepository) {
        this.nomisService = nomisService;
        this.repository = repository;
        this.prisonSupportedService = prisonSupportedService;
        this.statisticRepository = statisticRepository;
    }

    public KeyworkerStatsDto getStatsForStaff(Long staffId, String prisonId, final LocalDate fromDate, final LocalDate toDate) {

        Validate.notNull(staffId, "staffId");
        Validate.notNull(prisonId,"prisonId");

        CalcDateRange range = new CalcDateRange(fromDate, toDate);
        final LocalDateTime nextEndDate = range.getEndDate().atStartOfDay().plusDays(1);

        List<OffenderKeyworker> applicableAssignments = repository.findByStaffIdAndPrisonId(staffId, prisonId).stream()
                .filter(kw ->
                        kw.getAssignedDateTime().compareTo(nextEndDate) < 0 &&
                                (kw.getExpiryDateTime() == null || kw.getExpiryDateTime().compareTo(range.getStartDate().atStartOfDay()) >= 0))
                        .collect(Collectors.toList());

        List<String> prisonerNosList = applicableAssignments.stream().map(OffenderKeyworker::getOffenderNo).distinct().collect(Collectors.toList());

        if (!prisonerNosList.isEmpty()) {
            List<CaseNoteUsagePrisonersDto> usageCounts =
                    nomisService.getCaseNoteUsageForPrisoners(prisonerNosList, KEYWORKER_CASENOTE_TYPE, null, range.getStartDate(), range.getEndDate());

            Map<String, Integer> usageGroupedBySubType = usageCounts.stream()
                    .collect(Collectors.groupingBy(CaseNoteUsagePrisonersDto::getCaseNoteSubType,
                            Collectors.summingInt(CaseNoteUsagePrisonersDto::getNumCaseNotes)));

            Integer sessionCount = usageGroupedBySubType.get(KEYWORKER_SESSION_SUB_TYPE);
            int sessionsDone = sessionCount != null ? sessionCount : 0;

            int projectedKeyworkerSessions = getProjectedKeyworkerSessions(applicableAssignments, staffId, prisonId, range.getStartDate(), nextEndDate);
            final BigDecimal complianceRate = getComplianceRate(sessionsDone, projectedKeyworkerSessions);

            Integer entryCount = usageGroupedBySubType.get(KEYWORKER_ENTRY_SUB_TYPE);

            return KeyworkerStatsDto.builder()
                    .staffId(staffId)
                    .fromDate(range.getStartDate())
                    .toDate(range.getEndDate())
                    .projectedKeyworkerSessions(projectedKeyworkerSessions)
                    .complianceRate(complianceRate)
                    .caseNoteEntryCount(entryCount != null ? entryCount : 0)
                    .caseNoteSessionCount(sessionsDone)
                    .build();
        }
        return KeyworkerStatsDto.builder()
                .staffId(staffId)
                .fromDate(range.getStartDate())
                .toDate(range.getEndDate())
                .projectedKeyworkerSessions(0)
                .complianceRate(HUNDRED)
                .caseNoteEntryCount(0)
                .caseNoteSessionCount(0)
                .build();
    }

    public PrisonStatsDto getPrisonStats(String prisonId, final LocalDate fromDate, final LocalDate toDate) {
        Validate.notNull(prisonId,"prisonId");

        CalcDateRange range = new CalcDateRange(fromDate, toDate);
        Prison prisonConfig = prisonSupportedService.getPrisonDetail(prisonId);

        LocalDate nextDay = range.getEndDate().plusDays(1);

        SummaryStatistic current = getSummaryStatistic(statisticRepository.getAggregatedData(prisonId, range.getStartDate(), nextDay),
                range.getStartDate(), nextDay, prisonConfig.getKwSessionFrequencyInWeeks());

        SummaryStatistic previous = getSummaryStatistic(statisticRepository.getAggregatedData(prisonId, range.getStartDate().minusMonths(1), nextDay.minusMonths(1)),
                range.getStartDate().minusMonths(1), nextDay.minusMonths(1), prisonConfig.getKwSessionFrequencyInWeeks());

        List<PrisonKeyWorkerStatistic> dailyStats = statisticRepository.findByPrisonIdAndSnapshotDateBetween(prisonId, nextDay.minusYears(1), nextDay);

        Map<LocalDate, LongSummaryStatistics> kwSummary = dailyStats.stream().collect(
                Collectors.groupingBy(s -> s.getSnapshotDate().with(nextDay.getDayOfWeek()),
                        Collectors.summarizingLong(PrisonKeyWorkerStatistic::getNumberKeyWorkeringSessions))
        );

        Map<LocalDate, LongSummaryStatistics> compliance = dailyStats.stream().collect(
                Collectors.groupingBy(s -> s.getSnapshotDate().with(nextDay.getDayOfWeek()),
                        Collectors.summarizingLong(p ->
                                getComplianceRate(p.getNumberKeyWorkeringSessions(), p.getNumPrisonersAssignedKeyWorker()).multiply(HUNDRED).longValue()))
        );

        List<ImmutablePair<LocalDate, Long>> keyworkerSessionsTimeline = kwSummary.entrySet()
                .stream()
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .map(e -> new ImmutablePair<>(e.getKey(), (long)Math.floor(e.getValue().getAverage())))
                .collect(Collectors.toList());

        List<ImmutablePair<LocalDate, BigDecimal>> complianceTimeline = compliance.entrySet()
                .stream()
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .map(e -> new ImmutablePair<>(e.getKey(), new BigDecimal(e.getValue().getAverage() / 100.00).setScale(2, BigDecimal.ROUND_HALF_UP)))
                .collect(Collectors.toList());

        return PrisonStatsDto.builder()
                .prisonId(prisonId)
                .fromDate(range.getStartDate())
                .toDate(range.getEndDate())
                .current(current)
                .previous(previous)
                .keyworkerSessionsTimeline(keyworkerSessionsTimeline)
                .complianceTimeline(complianceTimeline)
                .build();
    }

    private SummaryStatistic getSummaryStatistic(List<PrisonKeyWorkerAgregatedStats> statList, LocalDate startDate, LocalDate endDate, int kwSessionFrequencyInWeeks) {

        if (!statList.isEmpty()) {
            final PrisonKeyWorkerAgregatedStats prisonStats = statList.get(0);
            long sessionMultiplier = Math.floorDiv(WEEKS.between(startDate, endDate), kwSessionFrequencyInWeeks);
            long projectedSessions = Math.round(prisonStats.getNumPrisonersAssignedKeyWorker() * sessionMultiplier);

            return SummaryStatistic.builder()
                    .avgNumDaysFromReceptionToAlliocationDays(prisonStats.getAvgNumDaysFromReceptionToAlliocationDays().intValue())
                    .avgNumDaysFromReceptionToKeyWorkingSession(prisonStats.getAvgNumDaysFromReceptionToKeyWorkingSession().intValue())
                    .numberKeyWorkerEntries(prisonStats.getNumberKeyWorkerEntries().intValue())
                    .numberKeyWorkeringSessions(prisonStats.getNumberKeyWorkeringSessions().intValue())
                    .numberOfActiveKeyworkers(prisonStats.getNumberOfActiveKeyworkers().intValue())
                    .totalNumPrisoners(prisonStats.getTotalNumPrisoners().intValue())
                    .numPrisonersAssignedKeyWorker(prisonStats.getNumPrisonersAssignedKeyWorker().intValue())
                    .percentagePrisonersWithKeyworker((int) (prisonStats.getNumPrisonersAssignedKeyWorker() * 100.00 / prisonStats.getTotalNumPrisoners()))
                    .numProjectedKeyworkerSessions((int) projectedSessions)
                    .complianceRate(getComplianceRate(prisonStats.getNumberKeyWorkeringSessions(), projectedSessions))
                    .build();
        }
        return null;
    }

    private BigDecimal getComplianceRate(long sessionCount, long projectedKeyworkerSessions) {
        BigDecimal complianceRate = HUNDRED;

        if (projectedKeyworkerSessions > 0)  {
            complianceRate = new BigDecimal(sessionCount * 100.00 / (float)projectedKeyworkerSessions).setScale(2, RoundingMode.HALF_UP);
        }
        return complianceRate;
    }

    private int getProjectedKeyworkerSessions(List<OffenderKeyworker> filteredAllocations, Long staffId, String prisonId, LocalDate fromDate, LocalDateTime nextEndDate) {
        final Map<Long, LongSummaryStatistics> kwResults = filteredAllocations.stream()
                .collect(
                        Collectors.groupingBy(OffenderKeyworker::getStaffId, Collectors.summarizingLong(k -> k.getDaysAllocated(fromDate, nextEndDate.toLocalDate()))
                    ));
        LongSummaryStatistics longSummaryStatistics = kwResults.get(staffId);
        if (longSummaryStatistics != null) {
            Prison prisonConfig = prisonSupportedService.getPrisonDetail(prisonId);
            float averageNumberPrisoners = (float)longSummaryStatistics.getSum() / DAYS.between(fromDate, nextEndDate);
            long sessionMultiplier =  Math.floorDiv(WEEKS.between(fromDate, nextEndDate), prisonConfig.getKwSessionFrequencyInWeeks());

            return Math.round(averageNumberPrisoners * sessionMultiplier);
        }
        return 0;
    }

    @Getter
    private static class CalcDateRange {
        private final LocalDate startDate;
        private final LocalDate endDate;

        CalcDateRange(final LocalDate fromDate, final LocalDate toDate) {
            if (fromDate != null && toDate != null) {
                startDate = fromDate;
                endDate = toDate;
            } else if (fromDate != null) {
                startDate = fromDate;
                endDate = fromDate.plusMonths(1);
            } else if (toDate != null) {
                startDate = toDate.minusMonths(1);
                endDate = toDate;
            } else {
                endDate = LocalDate.now().minusDays(1);
                startDate = endDate.minusMonths(1);
            }
        }
    }
}
