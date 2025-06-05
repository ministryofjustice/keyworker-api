package uk.gov.justice.digital.hmpps.keyworker.services;

import com.microsoft.applicationinsights.TelemetryClient;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.digital.hmpps.keyworker.dto.CaseNoteUsagePrisonersDto;
import uk.gov.justice.digital.hmpps.keyworker.dto.KeyworkerStatSummary;
import uk.gov.justice.digital.hmpps.keyworker.dto.KeyworkerStatsDto;
import uk.gov.justice.digital.hmpps.keyworker.dto.PrisonKeyWorkerAggregatedStats;
import uk.gov.justice.digital.hmpps.keyworker.dto.PrisonStatsDto;
import uk.gov.justice.digital.hmpps.keyworker.dto.SummaryStatistic;
import uk.gov.justice.digital.hmpps.keyworker.model.AllocationType;
import uk.gov.justice.digital.hmpps.keyworker.model.OffenderKeyworker;
import uk.gov.justice.digital.hmpps.keyworker.model.PrisonKeyWorkerStatistic;
import uk.gov.justice.digital.hmpps.keyworker.repository.OffenderKeyworkerRepository;
import uk.gov.justice.digital.hmpps.keyworker.repository.PrisonKeyWorkerStatisticRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.time.temporal.ChronoUnit.DAYS;
import static java.time.temporal.ChronoUnit.WEEKS;
import static java.util.stream.Collectors.averagingDouble;
import static uk.gov.justice.digital.hmpps.keyworker.services.KeyworkerService.KEYWORKER_CASENOTE_TYPE;
import static uk.gov.justice.digital.hmpps.keyworker.services.KeyworkerService.KEYWORKER_ENTRY_SUB_TYPE;
import static uk.gov.justice.digital.hmpps.keyworker.services.KeyworkerService.KEYWORKER_SESSION_SUB_TYPE;

@Service
@Transactional(readOnly = true)
@Slf4j
@AllArgsConstructor
public class KeyworkerStatsService {

    private final NomisService nomisService;
    private final OffenderKeyworkerRepository offenderKeyworkerRepository;
    private final PrisonKeyWorkerStatisticRepository statisticRepository;
    private final PrisonSupportedService prisonSupportedService;

    private static final BigDecimal hundred = new BigDecimal("100.00");

    public KeyworkerStatsDto getStatsForStaff(final Long staffId, final String prisonId, final LocalDate fromDate, final LocalDate toDate) {

        Validate.notNull(staffId, "staffId");
        Validate.notNull(prisonId, "prisonId");

        final var range = new CalcDateRange(fromDate, toDate);
        final var nextEndDate = range.getEndDate().atStartOfDay().plusDays(1);

        final var applicableAssignments = offenderKeyworkerRepository.findByStaffIdAndPrisonId(staffId, prisonId).stream()
                .filter(kw ->
                        kw.getAssignedDateTime().isBefore(nextEndDate) &&
                                (kw.getExpiryDateTime() == null || !kw.getExpiryDateTime().isBefore(range.getStartDate().atStartOfDay())))
                .collect(Collectors.toList());

        final var prisonerNosList = applicableAssignments.stream().map(OffenderKeyworker::getOffenderNo).distinct().collect(Collectors.toList());

        if (!prisonerNosList.isEmpty()) {
            final var caseNoteSummary = new KeyWorkingCaseNoteSummary(
                    null,
                    prisonerNosList,
                    range.startDate,
                    range.endDate,
                    staffId);

            final var projectedKeyworkerSessions = getProjectedKeyworkerSessions(applicableAssignments, staffId, prisonId, range.getStartDate(), nextEndDate);
            final var complianceRate = rate(caseNoteSummary.getSessionsDone(), projectedKeyworkerSessions);

            return KeyworkerStatsDto.builder()
                    .staffId(staffId)
                    .fromDate(range.getStartDate())
                    .toDate(range.getEndDate())
                    .projectedKeyworkerSessions(projectedKeyworkerSessions)
                    .complianceRate(complianceRate)
                    .caseNoteEntryCount(caseNoteSummary.getEntriesDone())
                    .caseNoteSessionCount(caseNoteSummary.getSessionsDone())
                    .build();
        }
        return KeyworkerStatsDto.builder()
                .staffId(staffId)
                .fromDate(range.getStartDate())
                .toDate(range.getEndDate())
                .projectedKeyworkerSessions(0)
                .complianceRate(BigDecimal.ZERO)
                .caseNoteEntryCount(0)
                .caseNoteSessionCount(0)
                .build();
    }

    public KeyworkerStatSummary getPrisonStats(final List<String> prisonIds, final LocalDate fromDate, final LocalDate toDate) {
        final var statsMap = getStatsMap(fromDate, toDate, prisonIds);

        final var current = getSummaryStatistic(statsMap.values().stream().filter(p -> p.getCurrent() != null).map(PrisonStatsDto::getCurrent).collect(Collectors.toList()));
        final var previous = getSummaryStatistic(statsMap.values().stream().filter(p -> p.getPrevious() != null).map(PrisonStatsDto::getPrevious).collect(Collectors.toList()));

        final var complianceTimeline = new TreeMap<LocalDate, BigDecimal>();
        final var complianceCount = new TreeMap<LocalDate, Long>();

        statsMap.values().stream()
                .filter(s -> s.getComplianceTimeline() != null)
                .forEach(s -> s.getComplianceTimeline().forEach((key, value) -> {
                    complianceTimeline.merge(key, value, (a, b) -> b.add(a));
                    complianceCount.merge(key, 1L, Long::sum);
                }));

        final var averageComplianceTimeline = new TreeMap<LocalDate, BigDecimal>();
        complianceTimeline.forEach((k, v) ->
                averageComplianceTimeline.put(k, v.divide(new BigDecimal(complianceCount.get(k)), RoundingMode.HALF_UP)));

        final var keyworkerSessionsTimeline = new TreeMap<LocalDate, Long>();
        statsMap.values().stream()
                .filter(s -> s.getKeyworkerSessionsTimeline() != null)
                .forEach(s -> s.getKeyworkerSessionsTimeline()
                        .forEach((key, value) -> keyworkerSessionsTimeline.merge(key, value, Long::sum)));

        final var avgSessions = statsMap.values().stream()
                .filter(s -> s.getAvgOverallKeyworkerSessions() != null)
                .mapToInt(PrisonStatsDto::getAvgOverallKeyworkerSessions).average();

        final var avgCompliance = statsMap.values().stream()
                .filter(s -> s.getAvgOverallCompliance() != null)
                .mapToDouble(p -> p.getAvgOverallCompliance().doubleValue()).average();

        final var range = new CalcDateRange(fromDate, toDate);

        final var prisonStatsDto = PrisonStatsDto.builder()
                .requestedFromDate(range.getStartDate())
                .requestedToDate(range.getEndDate())
                .current(current != null && current.getDataRangeFrom() != null ? current : null)
                .previous(previous != null && previous.getDataRangeFrom() != null ? previous : null)
                .complianceTimeline(averageComplianceTimeline)
                .keyworkerSessionsTimeline(keyworkerSessionsTimeline)
                .avgOverallKeyworkerSessions(avgSessions.isPresent() ? (int) Math.ceil(avgSessions.getAsDouble()) : 0)
                .avgOverallCompliance(avgCompliance.isPresent() ? BigDecimal.valueOf(avgCompliance.getAsDouble()).setScale(2, RoundingMode.HALF_UP) : null)
                .build();

        return KeyworkerStatSummary.builder()
                .summary(prisonStatsDto)
                .prisons(statsMap)
                .build();
    }

    private SummaryStatistic getSummaryStatistic(final List<SummaryStatistic> stats) {
        if (!stats.isEmpty()) {
            final var currSessionAvg = stats.stream().filter(s -> s.getAvgNumDaysFromReceptionToKeyWorkingSession() != null).mapToInt(SummaryStatistic::getAvgNumDaysFromReceptionToKeyWorkingSession).average();
            final var currAllocAvg = stats.stream().filter(s -> s.getAvgNumDaysFromReceptionToAllocationDays() != null).mapToInt(SummaryStatistic::getAvgNumDaysFromReceptionToAllocationDays).average();
            final var currAvgCompliance = stats.stream().filter(s -> s.getComplianceRate() != null).mapToDouble(s -> s.getComplianceRate().doubleValue()).average();
            final var currPerKw = stats.stream().filter(s -> s.getPercentagePrisonersWithKeyworker() != null).mapToDouble(s -> s.getPercentagePrisonersWithKeyworker().doubleValue()).average();

            return SummaryStatistic.builder()
                    .dataRangeFrom(stats.stream().map(SummaryStatistic::getDataRangeFrom).min(LocalDate::compareTo).orElse(null))
                    .dataRangeTo(stats.stream().map(SummaryStatistic::getDataRangeTo).max(LocalDate::compareTo).orElse(null))
                    .totalNumPrisoners(stats.stream().mapToInt(SummaryStatistic::getTotalNumPrisoners).sum())
                    .totalNumEligiblePrisoners(stats.stream().mapToInt(SummaryStatistic::getTotalNumEligiblePrisoners).sum())
                    .numberOfActiveKeyworkers(stats.stream().mapToInt(SummaryStatistic::getNumberOfActiveKeyworkers).sum())
                    .numberKeyWorkerEntries(stats.stream().mapToInt(SummaryStatistic::getNumberKeyWorkerEntries).sum())
                    .numberKeyWorkerSessions(stats.stream().mapToInt(SummaryStatistic::getNumberKeyWorkerSessions).sum())
                    .numPrisonersAssignedKeyWorker(stats.stream().mapToInt(SummaryStatistic::getNumPrisonersAssignedKeyWorker).sum())
                    .numProjectedKeyworkerSessions(stats.stream().mapToInt(SummaryStatistic::getNumProjectedKeyworkerSessions).sum())
                    .avgNumDaysFromReceptionToKeyWorkingSession(currSessionAvg.isPresent() ? (int) Math.round(currSessionAvg.getAsDouble()) : null)
                    .avgNumDaysFromReceptionToAllocationDays(currAllocAvg.isPresent() ? (int) Math.round(currAllocAvg.getAsDouble()) : null)
                    .complianceRate(currAvgCompliance.isPresent() ? BigDecimal.valueOf(currAvgCompliance.getAsDouble()).setScale(2, RoundingMode.HALF_UP) : null)
                    .percentagePrisonersWithKeyworker(currPerKw.isPresent() ? BigDecimal.valueOf(currPerKw.getAsDouble()).setScale(2, RoundingMode.HALF_UP) : null)
                    .build();
        }
        return null;
    }

    private Map<String, PrisonStatsDto> getStatsMap(final LocalDate fromDate, final LocalDate toDate, final List<String> prisonIds) {
        final var range = new CalcDateRange(fromDate, toDate);
        final var currentData = statisticRepository.getAggregatedData(prisonIds, range.getStartDate(), range.getEndDate())
                .stream().collect(Collectors.toMap(PrisonKeyWorkerAggregatedStats::getPrisonId, Function.identity()));

        final var previousData = statisticRepository.getAggregatedData(prisonIds, range.getPreviousStartDate(), range.getStartDate().minusDays(1))
                .stream().collect(Collectors.toMap(PrisonKeyWorkerAggregatedStats::getPrisonId, Function.identity()));
        final var dailyStats = statisticRepository.findByPrisonIdInAndSnapshotDateBetween(prisonIds, range.getEndDate().minusYears(1), range.getEndDate())
                .stream().collect(Collectors.groupingBy(PrisonKeyWorkerStatistic::getPrisonId));

        return prisonIds.stream()
                .collect(Collectors.toMap(p -> p, p ->
                        getPrisonStatsDto(p, range, currentData.get(p), previousData.get(p), dailyStats.get(p))));
    }

    private PrisonStatsDto getPrisonStatsDto(final String prisonId, final CalcDateRange range, final PrisonKeyWorkerAggregatedStats currentData, final PrisonKeyWorkerAggregatedStats previousData, final List<PrisonKeyWorkerStatistic> dailyStats) {
        final var prisonConfig = prisonSupportedService.getPrisonDetail(prisonId);
        final var current = getSummaryStatistic(currentData, prisonConfig.getKwSessionFrequencyInWeeks());
        final var previous = getSummaryStatistic(previousData, prisonConfig.getKwSessionFrequencyInWeeks());

        final var weekAdjuster = TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY);

        final var prisonStats = PrisonStatsDto.builder()
                .requestedFromDate(range.getStartDate())
                .requestedToDate(range.getEndDate())
                .current(current)
                .previous(previous);

        if (dailyStats != null) {
            final var kwSummary = new TreeMap<>(dailyStats.stream().collect(
                    Collectors.groupingBy(s -> s.getSnapshotDate().with(weekAdjuster),
                            Collectors.summingLong(PrisonKeyWorkerStatistic::getNumberKeyWorkerSessions))
            ));
            prisonStats.keyworkerSessionsTimeline(kwSummary);
            prisonStats.avgOverallKeyworkerSessions((int) Math.floor(kwSummary.values().stream().collect(averagingDouble(p -> p))));

            final var compliance = new TreeMap<>(dailyStats.stream().collect(
                    Collectors.groupingBy(s -> s.getSnapshotDate().with(weekAdjuster),
                            Collectors.averagingDouble(p ->
                            {
                                var projectedKeyworkerSessions = Math.floorDiv(p.getTotalNumEligiblePrisoners(), prisonConfig.getKwSessionFrequencyInWeeks() * 7);
                                return rate(p.getNumberKeyWorkerSessions(), projectedKeyworkerSessions).doubleValue();
                            }))
            ).entrySet().stream().filter(e -> e.getValue() != null).collect(Collectors.toMap(Map.Entry::getKey,
                    e -> BigDecimal.valueOf(e.getValue()).setScale(2, RoundingMode.HALF_UP))));

            prisonStats.complianceTimeline(compliance);
            prisonStats.avgOverallCompliance(getAverageCompliance(compliance.values()));

        }
        return prisonStats.build();
    }

    private BigDecimal getAverageCompliance(final Collection<BigDecimal> values) {
        var sum = BigDecimal.ZERO;
        if (!values.isEmpty()) {
            for (final var val : values) {
                sum = sum.add(val);
            }
            return sum.divide(new BigDecimal(values.size()), RoundingMode.HALF_UP);
        }
        return null;
    }

    private SummaryStatistic getSummaryStatistic(final PrisonKeyWorkerAggregatedStats prisonStats, final int kwSessionFrequencyInWeeks) {

        if (prisonStats != null) {
            final var sessionMultiplier = (DAYS.between(prisonStats.getStartDate(), prisonStats.getEndDate()) + 1) / (double) (kwSessionFrequencyInWeeks * 7);
            final var projectedSessions = Math.round(Math.floor(prisonStats.getTotalNumEligiblePrisoners()) * sessionMultiplier);

            return SummaryStatistic.builder()
                    .dataRangeFrom(prisonStats.getStartDate())
                    .dataRangeTo(prisonStats.getEndDate())
                    .avgNumDaysFromReceptionToAllocationDays(prisonStats.getAvgNumDaysFromReceptionToAllocationDays() != null ? prisonStats.getAvgNumDaysFromReceptionToAllocationDays().intValue() : null)
                    .avgNumDaysFromReceptionToKeyWorkingSession(prisonStats.getAvgNumDaysFromReceptionToKeyWorkingSession() != null ? prisonStats.getAvgNumDaysFromReceptionToKeyWorkingSession().intValue() : null)
                    .numberKeyWorkerEntries(prisonStats.getNumberKeyWorkerEntries().intValue())
                    .numberKeyWorkerSessions(prisonStats.getNumberKeyWorkerSessions().intValue())
                    .numberOfActiveKeyworkers(prisonStats.getNumberOfActiveKeyworkers().intValue())
                    .totalNumPrisoners(prisonStats.getTotalNumPrisoners().intValue())
                    .totalNumEligiblePrisoners(prisonStats.getTotalNumEligiblePrisoners().intValue())
                    .numPrisonersAssignedKeyWorker(prisonStats.getNumPrisonersAssignedKeyWorker().intValue())
                    .percentagePrisonersWithKeyworker(percentage(prisonStats.getNumPrisonersAssignedKeyWorker(), prisonStats.getTotalNumEligiblePrisoners()))
                    .numProjectedKeyworkerSessions((int) projectedSessions)
                    .complianceRate(rate(prisonStats.getNumberKeyWorkerSessions(), projectedSessions))
                    .build();
        }
        return null;
    }

    static BigDecimal percentage(final double numerator, final double denominator) {
        var percentage = hundred;

        if (denominator > 0) {
            percentage = new BigDecimal(numerator * 100.00 / denominator).setScale(2, RoundingMode.HALF_UP);
        }
        return percentage;
    }

    static BigDecimal rate(final long numerator, final double denominator) {
        var complianceRate = hundred;

        if (denominator > 0) {
            complianceRate = new BigDecimal(numerator * 100.00 / denominator).setScale(2, RoundingMode.HALF_UP);
        }
        return complianceRate;
    }

    private int getProjectedKeyworkerSessions(final List<OffenderKeyworker> filteredAllocations, final Long staffId, final String prisonId, final LocalDate fromDate, final LocalDateTime nextEndDate) {
        final var kwResults = filteredAllocations.stream()
                .collect(
                        Collectors.groupingBy(OffenderKeyworker::getStaffId, Collectors.summarizingLong(k -> k.getDaysAllocated(fromDate, nextEndDate.toLocalDate()))
                        ));
        final var longSummaryStatistics = kwResults.get(staffId);
        if (longSummaryStatistics != null) {
            final var prisonConfig = prisonSupportedService.getPrisonDetail(prisonId);
            final var averageNumberPrisoners = (float) longSummaryStatistics.getSum() / DAYS.between(fromDate, nextEndDate);
            final var sessionMultiplier = Math.floorDiv(WEEKS.between(fromDate, nextEndDate), prisonConfig.getKwSessionFrequencyInWeeks());

            return Math.round(averageNumberPrisoners * sessionMultiplier);
        }
        return 0;
    }

    @Getter
    private static class CalcDateRange {
        private final LocalDate startDate;
        private final LocalDate endDate;
        private final LocalDate previousStartDate;
        private final LocalDate nextDay;

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
                endDate = LocalDate.now();
                startDate = endDate.minusMonths(1);
            }

            nextDay = endDate.plusDays(1);
            final var periodInDays = DAYS.between(startDate, nextDay);
            previousStartDate = startDate.minusDays(periodInDays);

        }
    }

    @Getter
    private class KeyWorkingCaseNoteSummary {
        private final int sessionsDone;
        private final int entriesDone;
        private final List<CaseNoteUsagePrisonersDto> usageCounts;

        KeyWorkingCaseNoteSummary(final String prisonId, final List<String> offenderNos,
                                  final LocalDate start, final LocalDate end,
                                  final Long staffId) {

            if (prisonId != null) {
                usageCounts = nomisService.getCaseNoteUsageByPrison(prisonId, KEYWORKER_CASENOTE_TYPE, null, start, end);
            } else {
                usageCounts = nomisService.getCaseNoteUsageForPrisoners(null, offenderNos, staffId, KEYWORKER_CASENOTE_TYPE, null, start, end);
            }
            final var usageGroupedBySubType = usageCounts.stream()
                    .collect(Collectors.groupingBy(CaseNoteUsagePrisonersDto::getCaseNoteSubType,
                            Collectors.summingInt(CaseNoteUsagePrisonersDto::getNumCaseNotes)));

            final var sessionCount = usageGroupedBySubType.get(KEYWORKER_SESSION_SUB_TYPE);
            final var entryCount = usageGroupedBySubType.get(KEYWORKER_ENTRY_SUB_TYPE);

            sessionsDone = sessionCount != null ? sessionCount : 0;
            entriesDone = entryCount != null ? entryCount : 0;
        }
    }
}
