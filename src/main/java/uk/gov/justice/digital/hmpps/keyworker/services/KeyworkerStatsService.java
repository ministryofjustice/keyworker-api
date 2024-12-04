package uk.gov.justice.digital.hmpps.keyworker.services;

import com.microsoft.applicationinsights.TelemetryClient;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.digital.hmpps.keyworker.dto.CaseNoteUsagePrisonersDto;
import uk.gov.justice.digital.hmpps.keyworker.dto.KeyworkerStatSummary;
import uk.gov.justice.digital.hmpps.keyworker.dto.KeyworkerStatsDto;
import uk.gov.justice.digital.hmpps.keyworker.dto.OffenderLocationDto;
import uk.gov.justice.digital.hmpps.keyworker.dto.PagingAndSortingDto;
import uk.gov.justice.digital.hmpps.keyworker.dto.PrisonKeyWorkerAggregatedStats;
import uk.gov.justice.digital.hmpps.keyworker.dto.PrisonStatsDto;
import uk.gov.justice.digital.hmpps.keyworker.dto.SortOrder;
import uk.gov.justice.digital.hmpps.keyworker.dto.SummaryStatistic;
import uk.gov.justice.digital.hmpps.keyworker.model.AllocationType;
import uk.gov.justice.digital.hmpps.keyworker.model.KeyworkerStatus;
import uk.gov.justice.digital.hmpps.keyworker.model.OffenderKeyworker;
import uk.gov.justice.digital.hmpps.keyworker.model.PrisonKeyWorkerStatistic;
import uk.gov.justice.digital.hmpps.keyworker.repository.KeyworkerRepository;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.time.temporal.ChronoUnit.DAYS;
import static java.time.temporal.ChronoUnit.WEEKS;
import static java.util.stream.Collectors.averagingDouble;
import static java.util.stream.Collectors.averagingLong;
import static uk.gov.justice.digital.hmpps.keyworker.services.KeyworkerService.KEYWORKER_CASENOTE_TYPE;
import static uk.gov.justice.digital.hmpps.keyworker.services.KeyworkerService.KEYWORKER_ENTRY_SUB_TYPE;
import static uk.gov.justice.digital.hmpps.keyworker.services.KeyworkerService.KEYWORKER_SESSION_SUB_TYPE;
import static uk.gov.justice.digital.hmpps.keyworker.services.KeyworkerService.TRANSFER_CASENOTE_TYPE;

@Service
@Transactional(readOnly = true)
@Slf4j
public class KeyworkerStatsService {

    private final NomisService nomisService;
    private final OffenderKeyworkerRepository offenderKeyworkerRepository;
    private final KeyworkerRepository keyworkerRepository;
    private final PrisonKeyWorkerStatisticRepository statisticRepository;
    private final PrisonSupportedService prisonSupportedService;
    private final TelemetryClient telemetryClient;
    private final ComplexityOfNeed complexityOfNeedService;

    private final static BigDecimal HUNDRED = new BigDecimal("100.00");

    public KeyworkerStatsService(final NomisService nomisService, final PrisonSupportedService prisonSupportedService,
                                 final OffenderKeyworkerRepository offenderKeyworkerRepository,
                                 final PrisonKeyWorkerStatisticRepository statisticRepository,
                                 final KeyworkerRepository keyworkerRepository,
                                 final TelemetryClient telemetryClient,
                                 final ComplexityOfNeed complexityOfNeedService) {
        this.nomisService = nomisService;
        this.offenderKeyworkerRepository = offenderKeyworkerRepository;
        this.prisonSupportedService = prisonSupportedService;
        this.statisticRepository = statisticRepository;
        this.keyworkerRepository = keyworkerRepository;
        this.telemetryClient = telemetryClient;
        this.complexityOfNeedService = complexityOfNeedService;
    }

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

    @Transactional
    public PrisonKeyWorkerStatistic generatePrisonStats(final String prisonId, LocalDate snapshotDate) {
        Validate.notNull(prisonId, "prisonId");

        var dailyStat = statisticRepository.findOneByPrisonIdAndSnapshotDate(prisonId, snapshotDate);

        if (dailyStat != null) {
            log.warn("Statistics have already been generated for {} on {}", prisonId, snapshotDate);

        } else {
            // get all offenders in prison at the moment
            final var activePrisoners = nomisService.getOffendersAtLocation(prisonId, "bookingId", SortOrder.ASC, true);
            log.info("There are currently {} prisoners in {}", activePrisoners.size(), prisonId);

            // get a distinct list of offenderNos
            final var offenderNos = activePrisoners.stream().map(OffenderLocationDto::getOffenderNo).distinct().collect(Collectors.toList());

            // get a list of the eligible offenders (i.e. not high-complexity)
            final var eligibleOffenderNos = complexityOfNeedService.removeOffendersWithHighComplexityOfNeed(prisonId, new HashSet<>(offenderNos));

            // list of active key worker active assignments
            final var allocatedKeyWorkers = offenderKeyworkerRepository.findByActiveAndPrisonIdAndOffenderNoInAndAllocationTypeIsNot(true, prisonId, offenderNos, AllocationType.PROVISIONAL);
            log.info("There are currently {} allocated key workers to prisoners in {}", allocatedKeyWorkers.size(), prisonId);

            final var pagingAndSorting = PagingAndSortingDto.builder()
                    .pageLimit(3000L)
                    .pageOffset(0L)
                    .sortFields("staffId")
                    .sortOrder(SortOrder.ASC)
                    .build();
            final var activeKeyWorkers = nomisService.getActiveStaffKeyWorkersForPrison(prisonId, Optional.empty(), pagingAndSorting, true);

            // remove key workers not active
            final var keyWorkers = activeKeyWorkers.getBody().stream().filter(
                    kw -> {
                        var keyworker = keyworkerRepository.findById(kw.getStaffId()).orElse(null);
                        return keyworker == null || keyworker.getStatus() == KeyworkerStatus.ACTIVE;
                    }
            ).toList();
            log.info("There are currently {} active key workers in {}", keyWorkers.size(), prisonId);

            final var newAllocationsOnly = getNewAllocations(prisonId, snapshotDate);

            final var caseNoteSummary = new KeyWorkingCaseNoteSummary(
                    prisonId,
                    null,
                    snapshotDate,
                    snapshotDate,
                    null);

            log.info("There were {} Key Working Sessions and {} Key working entries on {}", caseNoteSummary.sessionsDone, caseNoteSummary.entriesDone, snapshotDate);

            final var offendersWithSessions = getOffendersWithKeyWorkerSessions(snapshotDate, caseNoteSummary);
            final var receptionDatesForOffenders = getReceptionDatesForOffenders(newAllocationsOnly, offendersWithSessions);

            Integer averageDaysToAllocation = null;
            Integer avgDaysReceptionToKWSession = null;

            if (!receptionDatesForOffenders.isEmpty()) {
                // find out when each prisoner entered this prison from this `receptionCheckList` list - last transfer
                final var transfers = getRecentTransfers(prisonId, snapshotDate, receptionDatesForOffenders);
                log.info("There are {} transfers in for prison {}", transfers.size(), prisonId);

                final var offenderReceptionMap = transfers.stream().collect(
                        Collectors.toMap(CaseNoteUsagePrisonersDto::getOffenderNo, CaseNoteUsagePrisonersDto::getLatestCaseNote));

                // calc average time to this allocation from reception
                final var offendersToIncludeInAverage = newAllocationsOnly.stream()
                        .filter(okw -> offenderReceptionMap.get(okw.getOffenderNo()) != null)
                        .toList();

                if (!offendersToIncludeInAverage.isEmpty()) {
                    final var days = offendersToIncludeInAverage.stream()
                            .collect(averagingLong(okw -> DAYS.between(offenderReceptionMap.get(okw.getOffenderNo()), okw.getAssignedDateTime())));
                    log.info("Average number of days until allocation {}", days);
                    averageDaysToAllocation = (int) Math.round(days);

                }

                if (!offendersWithSessions.isEmpty()) {
                    avgDaysReceptionToKWSession = getAvgDaysReceptionToKWSession(snapshotDate, caseNoteSummary, offendersWithSessions, offenderReceptionMap);
                }
            }

            dailyStat = PrisonKeyWorkerStatistic.builder()
                    .prisonId(prisonId)
                    .snapshotDate(snapshotDate)
                    .numPrisonersAssignedKeyWorker(allocatedKeyWorkers.size())
                    .totalNumPrisoners(activePrisoners.size())
                    .totalNumEligiblePrisoners(eligibleOffenderNos.size())
                    .numberKeyWorkerEntries(caseNoteSummary.entriesDone)
                    .numberKeyWorkerSessions(caseNoteSummary.sessionsDone)
                    .numberOfActiveKeyworkers(keyWorkers.size())
                    .avgNumDaysFromReceptionToAllocationDays(averageDaysToAllocation)
                    .avgNumDaysFromReceptionToKeyWorkingSession(avgDaysReceptionToKWSession)
                    .build();

            statisticRepository.save(dailyStat);

            logEventToAzure(dailyStat);
        }

        return dailyStat;
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

    private List<String> getReceptionDatesForOffenders(final List<OffenderKeyworker> newAllocationsOnly, final List<String> offendersWithSessions) {
        final List<String> receptionCheckList = new ArrayList<>();
        if (!offendersWithSessions.isEmpty() || !newAllocationsOnly.isEmpty()) {
            receptionCheckList.addAll(Stream.concat(newAllocationsOnly.stream().map(OffenderKeyworker::getOffenderNo), offendersWithSessions.stream())
                    .distinct().toList());
            log.info("There are {} offenders where we need to check their reception date", receptionCheckList.size());
        }
        return receptionCheckList;
    }

    private List<String> getOffendersWithKeyWorkerSessions(final LocalDate snapshotDate, final KeyWorkingCaseNoteSummary caseNoteSummary) {
        final List<String> offendersWithSessions = new ArrayList<>();
        if (caseNoteSummary.sessionsDone > 0) {
            offendersWithSessions.addAll(caseNoteSummary.usageCounts.stream()
                    .filter(cn -> cn.getCaseNoteSubType().equals(KEYWORKER_SESSION_SUB_TYPE))
                    .map(CaseNoteUsagePrisonersDto::getOffenderNo)
                    .distinct()
                    .toList());
            log.info("There are {} offenders with key work sessions on {}", offendersWithSessions.size(), snapshotDate);
        }
        return offendersWithSessions;
    }

    private List<CaseNoteUsagePrisonersDto> getRecentTransfers(final String prisonId, final LocalDate snapshotDate, final List<String> receptionCheckList) {
        final var prison = prisonSupportedService.getPrisonDetail(prisonId);

        final var earliestDate = snapshotDate.minusMonths(6).atStartOfDay();
        final var furthestCaseNoteTime = prison.getMigratedDateTime().isBefore(earliestDate) ? earliestDate : prison.getMigratedDateTime();
        log.info("Looking back to {} for transfers into prison {}", furthestCaseNoteTime, prisonId);

        return nomisService.getCaseNoteUsageForPrisoners(receptionCheckList, null, TRANSFER_CASENOTE_TYPE, null, furthestCaseNoteTime.toLocalDate(), snapshotDate.plusDays(1));
    }

    private Integer getAvgDaysReceptionToKWSession(final LocalDate snapshotDate, final KeyWorkingCaseNoteSummary caseNoteSummary, final List<String> offendersWithSessions, final Map<String, LocalDate> offenderReceptionMap) {
        // find out if this KW session is the first - look for case notes before this date.
        final var previousCaseNotes = nomisService.getCaseNoteUsageForPrisoners(offendersWithSessions, null,
                KEYWORKER_CASENOTE_TYPE, KEYWORKER_SESSION_SUB_TYPE, snapshotDate.minusMonths(6), snapshotDate.minusDays(1));

        final var previousCaseNoteMap = previousCaseNotes.stream().collect(
                Collectors.toMap(CaseNoteUsagePrisonersDto::getOffenderNo, CaseNoteUsagePrisonersDto::getLatestCaseNote));

        final var caseNotesToConsider = caseNoteSummary.usageCounts.stream()
                .filter(cn -> cn.getCaseNoteSubType().equals(KEYWORKER_SESSION_SUB_TYPE))
                .filter(cn -> offenderReceptionMap.get(cn.getOffenderNo()) != null)
                .filter(cn -> previousCaseNoteMap.get(cn.getOffenderNo()) == null ||
                        previousCaseNoteMap.get(cn.getOffenderNo()).isBefore(offenderReceptionMap.get(cn.getOffenderNo())))
                .toList();

        Double avgDaysReceptionToKWSession = null;
        if (!caseNotesToConsider.isEmpty()) {
            avgDaysReceptionToKWSession = caseNotesToConsider.stream().collect(averagingLong(cn -> DAYS.between(offenderReceptionMap.get(cn.getOffenderNo()), cn.getLatestCaseNote())));
            log.info("Average number of days until first KW Session {}", avgDaysReceptionToKWSession);
        }

        return avgDaysReceptionToKWSession != null ? (int) Math.round(avgDaysReceptionToKWSession) : null;
    }

    private List<OffenderKeyworker> getNewAllocations(final String prisonId, final LocalDate snapshotDate) {

        final var allocatedThisPeriod = offenderKeyworkerRepository.findByPrisonIdAndAssignedDateTimeBetween(prisonId, snapshotDate.atStartOfDay(), snapshotDate.plusDays(1).atStartOfDay());
        log.info("There were {} key worker allocations done in {} on {}", allocatedThisPeriod.size(), prisonId, snapshotDate);

        final List<OffenderKeyworker> newAllocationsOnly = new ArrayList<>();

        if (!allocatedThisPeriod.isEmpty()) {
            final var offenderNosAllocatedThisPeriod = allocatedThisPeriod.stream().map(OffenderKeyworker::getOffenderNo).collect(Collectors.toSet());

            // find out if this is the first allocation to a KW in this prison
            final var previousAllocations = offenderKeyworkerRepository.findByPrisonIdAndAssignedDateTimeBeforeAndOffenderNoInAndAllocationTypeIsNot(prisonId, snapshotDate.atStartOfDay(), offenderNosAllocatedThisPeriod, AllocationType.PROVISIONAL);
            final var offendersAlreadyAllocated = previousAllocations.stream().map(OffenderKeyworker::getOffenderNo).distinct().toList();
            log.info("Of these allocations {} had previous offender allocations in this {} prison, and will be excluded.", offendersAlreadyAllocated.size(), prisonId);

            newAllocationsOnly.addAll(allocatedThisPeriod.stream().filter(okw -> !offendersAlreadyAllocated.contains(okw.getOffenderNo())).toList());
        }
        log.info("Therefore there are {} new allocations in {} prison", newAllocationsOnly.size(), prisonId);
        return newAllocationsOnly;

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
        var percentage = HUNDRED;

        if (denominator > 0) {
            percentage = new BigDecimal(numerator * 100.00 / denominator).setScale(2, RoundingMode.HALF_UP);
        }
        return percentage;
    }

    static BigDecimal rate(final long numerator, final double denominator) {
        var complianceRate = HUNDRED;

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
                usageCounts = nomisService.getCaseNoteUsageForPrisoners(offenderNos, staffId, KEYWORKER_CASENOTE_TYPE, null, start, end);
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

    private void logEventToAzure(final PrisonKeyWorkerStatistic stats) {
        final var logMap = new HashMap<String, String>();
        logMap.put("snapshotDate", stats.getSnapshotDate().format(DateTimeFormatter.ISO_LOCAL_DATE));
        logMap.put("prisonId", stats.getPrisonId());

        final Map<String, Double> metrics = new HashMap<>();
        metrics.put("totalNumPrisoners", stats.getTotalNumPrisoners().doubleValue());
        metrics.put("totalNumEligiblePrisoners", stats.getTotalNumEligiblePrisoners().doubleValue());
        metrics.put("numPrisonersAssignedKeyWorker", stats.getNumPrisonersAssignedKeyWorker().doubleValue());
        metrics.put("numberOfActiveKeyworkers", stats.getNumberOfActiveKeyworkers().doubleValue());
        metrics.put("numberKeyWorkerEntries", stats.getNumberKeyWorkerEntries().doubleValue());
        metrics.put("numberKeyWorkerSessions", stats.getNumberKeyWorkerSessions().doubleValue());

        if (stats.getAvgNumDaysFromReceptionToAllocationDays() != null) {
            metrics.put("avgNumDaysFromReceptionToAllocationDays", stats.getAvgNumDaysFromReceptionToAllocationDays().doubleValue());
        }
        if (stats.getAvgNumDaysFromReceptionToKeyWorkingSession() != null) {
            metrics.put("avgNumDaysFromReceptionToKeyWorkingSession", stats.getAvgNumDaysFromReceptionToKeyWorkingSession().doubleValue());
        }
        telemetryClient.trackEvent("kwStatsGenerated", logMap, metrics);
    }

    public void raiseStatsProcessingError(final String prisonId, final Exception exception) {
        final var logMap = new HashMap<String, String>();
        logMap.put("snapshotDate", LocalDate.now().minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE));
        logMap.put("prisonId", prisonId);

        telemetryClient.trackException(exception, logMap, null);
    }
}
