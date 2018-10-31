package uk.gov.justice.digital.hmpps.keyworker.services;

import com.microsoft.applicationinsights.TelemetryClient;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.Exchange;
import org.apache.commons.lang3.Validate;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.digital.hmpps.keyworker.dto.*;
import uk.gov.justice.digital.hmpps.keyworker.model.AllocationType;
import uk.gov.justice.digital.hmpps.keyworker.model.OffenderKeyworker;
import uk.gov.justice.digital.hmpps.keyworker.model.PrisonKeyWorkerStatistic;
import uk.gov.justice.digital.hmpps.keyworker.repository.OffenderKeyworkerRepository;
import uk.gov.justice.digital.hmpps.keyworker.repository.PrisonKeyWorkerStatisticRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.time.temporal.ChronoUnit.DAYS;
import static java.time.temporal.ChronoUnit.WEEKS;
import static java.util.stream.Collectors.averagingDouble;
import static java.util.stream.Collectors.averagingLong;
import static uk.gov.justice.digital.hmpps.keyworker.services.KeyworkerService.*;

@Service
@Transactional(readOnly = true)
@Slf4j
public class KeyworkerStatsService {

    private final NomisService nomisService;
    private final OffenderKeyworkerRepository offenderKeyworkerRepository;
    private final PrisonKeyWorkerStatisticRepository statisticRepository;
    private final PrisonSupportedService prisonSupportedService;
    private final TelemetryClient telemetryClient;

    private final BigDecimal HUNDRED = new BigDecimal("100.00");

    public KeyworkerStatsService(NomisService nomisService, PrisonSupportedService prisonSupportedService,
                                 OffenderKeyworkerRepository offenderKeyworkerRepository,
                                 PrisonKeyWorkerStatisticRepository statisticRepository,
                                 TelemetryClient telemetryClient) {
        this.nomisService = nomisService;
        this.offenderKeyworkerRepository = offenderKeyworkerRepository;
        this.prisonSupportedService = prisonSupportedService;
        this.statisticRepository = statisticRepository;
        this.telemetryClient = telemetryClient;
    }

    public KeyworkerStatsDto getStatsForStaff(Long staffId, String prisonId, final LocalDate fromDate, final LocalDate toDate) {

        Validate.notNull(staffId, "staffId");
        Validate.notNull(prisonId,"prisonId");

        CalcDateRange range = new CalcDateRange(fromDate, toDate);
        final LocalDateTime nextEndDate = range.getEndDate().atStartOfDay().plusDays(1);

        List<OffenderKeyworker> applicableAssignments = offenderKeyworkerRepository.findByStaffIdAndPrisonId(staffId, prisonId).stream()
                .filter(kw ->
                        kw.getAssignedDateTime().compareTo(nextEndDate) < 0 &&
                                (kw.getExpiryDateTime() == null || kw.getExpiryDateTime().compareTo(range.getStartDate().atStartOfDay()) >= 0))
                        .collect(Collectors.toList());

        List<String> prisonerNosList = applicableAssignments.stream().map(OffenderKeyworker::getOffenderNo).distinct().collect(Collectors.toList());

        if (!prisonerNosList.isEmpty()) {
            KeyWorkingCaseNoteSummary caseNoteSummary = new KeyWorkingCaseNoteSummary(prisonerNosList, range.startDate, range.endDate, staffId, false);
            int projectedKeyworkerSessions = getProjectedKeyworkerSessions(applicableAssignments, staffId, prisonId, range.getStartDate(), nextEndDate);
            final BigDecimal complianceRate = getComplianceRate(caseNoteSummary.getSessionsDone(), projectedKeyworkerSessions);

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
    public PrisonKeyWorkerStatistic generatePrisonStats(String prisonId) {
        Validate.notNull(prisonId,"prisonId");

        final LocalDate snapshotDate = LocalDate.now().minusDays(1);

        PrisonKeyWorkerStatistic dailyStat = statisticRepository.findOneByPrisonIdAndSnapshotDate(prisonId, snapshotDate);

        if (dailyStat != null) {
            log.warn("Statistics have already been generated for {} on {}", prisonId, snapshotDate);

        } else {
            // get all offenders in prison at the moment
            final List<OffenderLocationDto> activePrisoners = nomisService.getOffendersAtLocation(prisonId, "bookingId", SortOrder.ASC, true);
            log.info("There are currently {} prisoners in {}", activePrisoners.size(), prisonId);

            // get a distinct list of offenderNos
            final List<String> offenderNos = activePrisoners.stream().map(OffenderLocationDto::getOffenderNo).distinct().collect(Collectors.toList());

            // list of active key worker active assignments
            final List<OffenderKeyworker> allocatedKeyWorkers = offenderKeyworkerRepository.findByActiveAndPrisonIdAndOffenderNoInAndAllocationTypeIsNot(true, prisonId, offenderNos, AllocationType.PROVISIONAL);
            log.info("There are currently {} allocated key workers to prisoners in {}", allocatedKeyWorkers.size(), prisonId);

            PagingAndSortingDto pagingAndSorting = PagingAndSortingDto.builder()
                    .pageLimit(3000L)
                    .pageOffset(0L)
                    .sortFields("staffId")
                    .sortOrder(SortOrder.ASC)
                    .build();
            ResponseEntity<List<StaffLocationRoleDto>> activeKeyWorkers = nomisService.getActiveStaffKeyWorkersForPrison(prisonId, Optional.empty(), pagingAndSorting, true);
            log.info("There are currently {} active key workers in {}", activeKeyWorkers.getBody().size(), prisonId);

            List<OffenderKeyworker> newAllocationsOnly = getNewAllocations(prisonId, snapshotDate);

            KeyWorkingCaseNoteSummary caseNoteSummary = new KeyWorkingCaseNoteSummary(offenderNos, snapshotDate, snapshotDate, null, true);
            log.info("There were {} Key Working Sessions and {} Key working entries on {}", caseNoteSummary.sessionsDone, caseNoteSummary.entriesDone, snapshotDate);

            final List<String> offendersWithSessions = getOffendersWithKeyWorkerSessions(snapshotDate, caseNoteSummary);
            final List<String> receptionDatesForOffenders = getReceptionDatesForOffenders(newAllocationsOnly, offendersWithSessions);

            Integer averageDaysToAllocation = null;
            Integer avgDaysReceptionToKWSession = null;

            if (receptionDatesForOffenders.size() > 0) {
                // find out when each prisoner entered this prison from this `receptionCheckList` list - last transfer
                List<CaseNoteUsagePrisonersDto> transfers = getRecentTransfers(prisonId, snapshotDate, receptionDatesForOffenders);
                log.info("There are {} transfers in for prison {}", transfers.size(), prisonId);

                Map<String, LocalDate> offenderReceptionMap = transfers.stream().collect(
                        Collectors.toMap(CaseNoteUsagePrisonersDto::getOffenderNo, CaseNoteUsagePrisonersDto::getLatestCaseNote));

                // calc average time to this allocation from reception
                Double days = newAllocationsOnly.stream()
                        .filter(okw -> offenderReceptionMap.get(okw.getOffenderNo()) != null)
                        .collect(averagingLong(okw -> DAYS.between(offenderReceptionMap.get(okw.getOffenderNo()), okw.getAssignedDateTime())));

                averageDaysToAllocation = days != null ? days.intValue() : null;
                log.info("Average number of days until allocation {}", averageDaysToAllocation);

                if (offendersWithSessions.size() > 0) {
                    avgDaysReceptionToKWSession = getAvgDaysReceptionToKWSession(snapshotDate, caseNoteSummary, offendersWithSessions, offenderReceptionMap);
                    log.info("Average number of days until first KW Session {}", avgDaysReceptionToKWSession);
                }
            }

            dailyStat = PrisonKeyWorkerStatistic.builder()
                    .prisonId(prisonId)
                    .snapshotDate(snapshotDate)
                    .numPrisonersAssignedKeyWorker(allocatedKeyWorkers.size())
                    .totalNumPrisoners(activePrisoners.size())
                    .numberKeyWorkerEntries(caseNoteSummary.entriesDone)
                    .numberKeyWorkeringSessions(caseNoteSummary.sessionsDone)
                    .numberOfActiveKeyworkers(activeKeyWorkers.getBody().size())
                    .avgNumDaysFromReceptionToAllocationDays(averageDaysToAllocation)
                    .avgNumDaysFromReceptionToKeyWorkingSession(avgDaysReceptionToKWSession)
                    .build();

            statisticRepository.save(dailyStat);

            logEventToAzure(dailyStat);
        }

        return dailyStat;
    }

    private List<String> getReceptionDatesForOffenders(List<OffenderKeyworker> newAllocationsOnly, List<String> offendersWithSessions) {
        final List<String> receptionCheckList = new ArrayList<>();
        if (offendersWithSessions.size() > 0 || newAllocationsOnly.size() > 0) {
            receptionCheckList.addAll(Stream.concat(newAllocationsOnly.stream().map(OffenderKeyworker::getOffenderNo), offendersWithSessions.stream())
                    .distinct().collect(Collectors.toList()));
            log.info("There are {} offenders where we need to check their reception date", receptionCheckList.size());
        }
        return receptionCheckList;
    }

    private List<String> getOffendersWithKeyWorkerSessions(LocalDate snapshotDate, KeyWorkingCaseNoteSummary caseNoteSummary) {
        final List<String> offendersWithSessions = new ArrayList<>();
        if (caseNoteSummary.sessionsDone > 0) {
            offendersWithSessions.addAll(caseNoteSummary.usageCounts.stream()
                    .filter(cn -> cn.getCaseNoteSubType().equals(KEYWORKER_SESSION_SUB_TYPE))
                    .map(CaseNoteUsagePrisonersDto::getOffenderNo)
                    .distinct()
                    .collect(Collectors.toList()));
            log.info("There are {} offenders with key work sessions on {}", offendersWithSessions.size(), snapshotDate);
        }
        return offendersWithSessions;
    }

    private List<CaseNoteUsagePrisonersDto> getRecentTransfers(String prisonId, LocalDate snapshotDate, List<String> receptionCheckList) {
        Prison prison = prisonSupportedService.getPrisonDetail(prisonId);

        LocalDateTime earliestDate = snapshotDate.minusMonths(6).atStartOfDay();
        LocalDateTime furthestCaseNoteTime = prison.getMigratedDateTime().isBefore(earliestDate) ? earliestDate : prison.getMigratedDateTime();
        log.info("Looking back to {} for transfers into prison {}", furthestCaseNoteTime, prisonId);

        return nomisService.getCaseNoteUsageForPrisoners(receptionCheckList, null, "TRANSFER", null, furthestCaseNoteTime.toLocalDate(), snapshotDate.plusDays(1), true);
    }

    private Integer getAvgDaysReceptionToKWSession(LocalDate snapshotDate, KeyWorkingCaseNoteSummary caseNoteSummary, List<String> offendersWithSessions, Map<String, LocalDate> offenderReceptionMap) {
        Double avgDaysReceptionToKWSession;// find out if this KW session is the first - look for case notes before this date.
        List<CaseNoteUsagePrisonersDto> previousCaseNotes = nomisService.getCaseNoteUsageForPrisoners(offendersWithSessions, null,
                KEYWORKER_CASENOTE_TYPE, KEYWORKER_SESSION_SUB_TYPE, snapshotDate.minusMonths(6), snapshotDate.minusDays(1), true);

        Map<String, LocalDate> previousCaseNoteMap = previousCaseNotes.stream().collect(
                        Collectors.toMap(CaseNoteUsagePrisonersDto::getOffenderNo, CaseNoteUsagePrisonersDto::getLatestCaseNote));

        avgDaysReceptionToKWSession = caseNoteSummary.usageCounts.stream()
                .filter(cn -> cn.getCaseNoteSubType().equals(KEYWORKER_SESSION_SUB_TYPE))
                .filter(cn -> offenderReceptionMap.get(cn.getOffenderNo()) != null)
                .filter(cn -> previousCaseNoteMap.get(cn.getOffenderNo()) == null ||
                        previousCaseNoteMap.get(cn.getOffenderNo()).isBefore(offenderReceptionMap.get(cn.getOffenderNo())))
                .collect(averagingLong(cn -> DAYS.between(offenderReceptionMap.get(cn.getOffenderNo()), cn.getLatestCaseNote())));
        return avgDaysReceptionToKWSession != null ? avgDaysReceptionToKWSession.intValue() : null;
    }

    private List<OffenderKeyworker> getNewAllocations(String prisonId, LocalDate snapshotDate) {

        List<OffenderKeyworker> allocatedThisPeriod = offenderKeyworkerRepository.findByAssignedDateTimeBetween(snapshotDate.atStartOfDay(), snapshotDate.plusDays(1).atStartOfDay());
        log.info("There were {} key worker allocations done in {} on {}", allocatedThisPeriod.size(), prisonId, snapshotDate);

        final List<OffenderKeyworker> newAllocationsOnly = new ArrayList<>();

        if (allocatedThisPeriod.size() > 0) {
            Set<String> offenderNosAllocatedThisPeriod = allocatedThisPeriod.stream().map(OffenderKeyworker::getOffenderNo).collect(Collectors.toSet());

            // find out if this is the first allocation to a KW in this prison
            List<OffenderKeyworker> previousAllocations = offenderKeyworkerRepository.findByPrisonIdAndAssignedDateTimeBeforeAndOffenderNoInAndAllocationTypeIsNot(prisonId, snapshotDate.atStartOfDay(), offenderNosAllocatedThisPeriod, AllocationType.PROVISIONAL);
            log.info("Of these allocations {} had previous allocations in this {} prison, and will be excluded.", previousAllocations.size(), prisonId);

            newAllocationsOnly.addAll(allocatedThisPeriod);
            newAllocationsOnly.removeAll(previousAllocations);
        }
        log.info("Therefore there are {} new allocations in {} prison", newAllocationsOnly.size(), prisonId);
        return newAllocationsOnly;

    }

    public PrisonStatsDto getPrisonStats(String prisonId, final LocalDate fromDate, final LocalDate toDate) {
        Validate.notNull(prisonId,"prisonId");

        CalcDateRange range = new CalcDateRange(fromDate, toDate);
        final Prison prisonConfig = prisonSupportedService.getPrisonDetail(prisonId);

        LocalDate nextDay = range.getEndDate().plusDays(1);

        SummaryStatistic current = getSummaryStatistic(statisticRepository.getAggregatedData(prisonId, range.getStartDate(), nextDay),
                range.getStartDate(), nextDay, prisonConfig.getKwSessionFrequencyInWeeks());

        SummaryStatistic previous = getSummaryStatistic(statisticRepository.getAggregatedData(prisonId, range.getStartDate().minusMonths(1), nextDay.minusMonths(1)),
                range.getStartDate().minusMonths(1), nextDay.minusMonths(1), prisonConfig.getKwSessionFrequencyInWeeks());

        List<PrisonKeyWorkerStatistic> dailyStats = statisticRepository.findByPrisonIdAndSnapshotDateBetween(prisonId, nextDay.minusYears(1), nextDay);

        SortedMap<LocalDate, Long> kwSummary = new TreeMap<>(dailyStats.stream().collect(
                Collectors.groupingBy(s -> s.getSnapshotDate().with(nextDay.getDayOfWeek()),
                        Collectors.summingLong(PrisonKeyWorkerStatistic::getNumberKeyWorkeringSessions))
        ));

        TreeMap<LocalDate, BigDecimal> compliance = new TreeMap<>(dailyStats.stream().collect(
                Collectors.groupingBy(s -> s.getSnapshotDate().with(nextDay.getDayOfWeek()),
                        Collectors.averagingDouble(p ->
                        {
                            int projectedKeyworkerSessions = Math.floorDiv(p.getNumPrisonersAssignedKeyWorker(), prisonConfig.getKwSessionFrequencyInWeeks() * 7);
                            return getComplianceRate(p.getNumberKeyWorkeringSessions(), projectedKeyworkerSessions).doubleValue();
                        }))
        ).entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
                e -> new BigDecimal(e.getValue()).setScale(2, BigDecimal.ROUND_HALF_UP))));

        int avgOverallKeyworkerSessions = (int)Math.floor(kwSummary.values().stream().collect(averagingDouble(p -> p)));

        return PrisonStatsDto.builder()
                .prisonId(prisonId)
                .requestedFromDate(range.getStartDate())
                .requestedToDate(range.getEndDate())
                .current(current)
                .previous(previous)
                .keyworkerSessionsTimeline(kwSummary)
                .avgOverallKeyworkerSessions(avgOverallKeyworkerSessions)
                .complianceTimeline(compliance)
                .avgOverallCompliance(getAverageCompliance(compliance.values()))
                .build();
    }

    private BigDecimal getAverageCompliance(Collection<BigDecimal> complianceValues) {
        if (complianceValues != null && !complianceValues.isEmpty()) {
            BigDecimal[] totalWithCount
                    = complianceValues.stream()
                    .map(bd -> new BigDecimal[]{bd, BigDecimal.ONE})
                    .reduce((a, b) -> new BigDecimal[]{a[0].add(b[0]), a[1].add(BigDecimal.ONE)})
                    .get();
            return totalWithCount[0].divide(totalWithCount[1], RoundingMode.HALF_UP);
        }
        return null;
    }

    private SummaryStatistic getSummaryStatistic(List<PrisonKeyWorkerAggregatedStats> statList, LocalDate startDate, LocalDate endDate, int kwSessionFrequencyInWeeks) {

        if (!statList.isEmpty()) {
            final PrisonKeyWorkerAggregatedStats prisonStats = statList.get(0);
            long sessionMultiplier = Math.floorDiv(WEEKS.between(startDate, endDate), kwSessionFrequencyInWeeks);
            long projectedSessions = Math.round(prisonStats.getNumPrisonersAssignedKeyWorker() * sessionMultiplier);

            return SummaryStatistic.builder()
                    .dataRangeFrom(prisonStats.getStartDate())
                    .dataRangeTo(prisonStats.getEndDate())
                    .avgNumDaysFromReceptionToAllocationDays(prisonStats.getAvgNumDaysFromReceptionToAllocationDays() != null ? prisonStats.getAvgNumDaysFromReceptionToAllocationDays().intValue() : null)
                    .avgNumDaysFromReceptionToKeyWorkingSession(prisonStats.getAvgNumDaysFromReceptionToKeyWorkingSession() != null ? prisonStats.getAvgNumDaysFromReceptionToKeyWorkingSession().intValue() : null)
                    .numberKeyWorkerEntries(prisonStats.getNumberKeyWorkerEntries().intValue())
                    .numberKeyWorkeringSessions(prisonStats.getNumberKeyWorkeringSessions().intValue())
                    .numberOfActiveKeyworkers(prisonStats.getNumberOfActiveKeyworkers().intValue())
                    .totalNumPrisoners(prisonStats.getTotalNumPrisoners().intValue())
                    .numPrisonersAssignedKeyWorker(prisonStats.getNumPrisonersAssignedKeyWorker().intValue())
                    .percentagePrisonersWithKeyworker(new BigDecimal(prisonStats.getNumPrisonersAssignedKeyWorker() * 100.00 / prisonStats.getTotalNumPrisoners()).setScale(2, BigDecimal.ROUND_HALF_UP))
                    .numProjectedKeyworkerSessions((int) projectedSessions)
                    .complianceRate(getComplianceRate(prisonStats.getNumberKeyWorkeringSessions(), projectedSessions))
                    .build();
        }
        return null;
    }

    private BigDecimal getComplianceRate(long sessionCount, double projectedKeyworkerSessions) {
        BigDecimal complianceRate = HUNDRED;

        if (projectedKeyworkerSessions > 0)  {
            complianceRate = new BigDecimal(sessionCount * 100.00 / projectedKeyworkerSessions).setScale(2, RoundingMode.HALF_UP);
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

    @Getter
    private class KeyWorkingCaseNoteSummary {
        private final int sessionsDone;
        private final int entriesDone;
        private final List<CaseNoteUsagePrisonersDto> usageCounts;

        KeyWorkingCaseNoteSummary(List<String> offenderNos, LocalDate start, LocalDate end, Long staffId, boolean admin) {

           usageCounts = nomisService.getCaseNoteUsageForPrisoners(offenderNos, staffId, KEYWORKER_CASENOTE_TYPE, null, start, end, admin);

            final Map<String, Integer> usageGroupedBySubType = usageCounts.stream()
                    .collect(Collectors.groupingBy(CaseNoteUsagePrisonersDto::getCaseNoteSubType,
                            Collectors.summingInt(CaseNoteUsagePrisonersDto::getNumCaseNotes)));

            Integer sessionCount = usageGroupedBySubType.get(KEYWORKER_SESSION_SUB_TYPE);
            Integer entryCount = usageGroupedBySubType.get(KEYWORKER_ENTRY_SUB_TYPE);

            sessionsDone = sessionCount != null ? sessionCount : 0;
            entriesDone = entryCount != null ? entryCount : 0;
        }
    }

    private void logEventToAzure(PrisonKeyWorkerStatistic stats) {
        final Map<String, String> logMap = new HashMap<>();
        logMap.put("snapshotDate", stats.getSnapshotDate().format(DateTimeFormatter.ISO_LOCAL_DATE));
        logMap.put("prisonId", stats.getPrisonId());

        final Map<String, Double> metrics = new HashMap<>();
        metrics.put("totalNumPrisoners", stats.getTotalNumPrisoners().doubleValue());
        metrics.put("numPrisonersAssignedKeyWorker", stats.getNumPrisonersAssignedKeyWorker().doubleValue());
        metrics.put("numberOfActiveKeyworkers", stats.getNumberOfActiveKeyworkers().doubleValue());
        metrics.put("numberKeyWorkerEntries", stats.getNumberKeyWorkerEntries().doubleValue());
        metrics.put("numberKeyWorkeringSessions", stats.getNumberKeyWorkeringSessions().doubleValue());

        if (stats.getAvgNumDaysFromReceptionToAllocationDays() != null) {
            metrics.put("avgNumDaysFromReceptionToAllocationDays", stats.getAvgNumDaysFromReceptionToAllocationDays().doubleValue());
        }
        if (stats.getAvgNumDaysFromReceptionToKeyWorkingSession() != null) {
            metrics.put("avgNumDaysFromReceptionToKeyWorkingSession", stats.getAvgNumDaysFromReceptionToKeyWorkingSession().doubleValue());
        }
        telemetryClient.trackEvent("kwStatsGenerated", logMap, metrics);
    }

    public void raiseStatsProcessingError(String prisonId, Exchange exchange) {
        final Map<String, String> logMap = new HashMap<>();
        logMap.put("snapshotDate", LocalDate.now().minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE));
        logMap.put("prisonId", prisonId);

        telemetryClient.trackException(exchange.getException(), logMap, null);
    }
}
