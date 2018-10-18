package uk.gov.justice.digital.hmpps.keyworker.services;

import org.apache.commons.lang3.Validate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.digital.hmpps.keyworker.dto.CaseNoteUsagePrisonersDto;
import uk.gov.justice.digital.hmpps.keyworker.dto.KeyworkerStatsDto;
import uk.gov.justice.digital.hmpps.keyworker.dto.Prison;
import uk.gov.justice.digital.hmpps.keyworker.dto.PrisonStatsDto;
import uk.gov.justice.digital.hmpps.keyworker.model.OffenderKeyworker;
import uk.gov.justice.digital.hmpps.keyworker.repository.OffenderKeyworkerRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
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

    private final PrisonSupportedService prisonSupportedService;

    private final BigDecimal PERCENT_OPERAND = new BigDecimal("100.00");

    public KeyworkerStatsService(NomisService nomisService, PrisonSupportedService prisonSupportedService,
                                 OffenderKeyworkerRepository repository) {
        this.nomisService = nomisService;
        this.repository = repository;
        this.prisonSupportedService = prisonSupportedService;
    }

    public KeyworkerStatsDto getStatsForStaff(Long staffId, String prisonId, LocalDate fromDate, LocalDate toDate) {

        Validate.notNull(staffId, "staffId");
        Validate.notNull(prisonId,"prisonId");
        Validate.notNull(fromDate, "fromDate");
        Validate.notNull(toDate, "toDate");

        final LocalDateTime nextEndDate = toDate.atStartOfDay().plusDays(1);

        List<OffenderKeyworker> applicableAssignments = repository.findByStaffIdAndPrisonId(staffId, prisonId).stream()
                .filter(kw ->
                        kw.getAssignedDateTime().compareTo(nextEndDate) < 0 &&
                                (kw.getExpiryDateTime() == null || kw.getExpiryDateTime().compareTo(fromDate.atStartOfDay()) >= 0))
                        .collect(Collectors.toList());

        List<String> prisonerNosList = applicableAssignments.stream().map(OffenderKeyworker::getOffenderNo).distinct().collect(Collectors.toList());

        if (!prisonerNosList.isEmpty()) {
            List<CaseNoteUsagePrisonersDto> usageCounts =
                    nomisService.getCaseNoteUsageForPrisoners(prisonerNosList, KEYWORKER_CASENOTE_TYPE, null, fromDate, toDate);

            Map<String, Integer> usageGroupedBySubType = usageCounts.stream()
                    .collect(Collectors.groupingBy(CaseNoteUsagePrisonersDto::getCaseNoteSubType,
                            Collectors.summingInt(CaseNoteUsagePrisonersDto::getNumCaseNotes)));

            Integer sessionCount = usageGroupedBySubType.get(KEYWORKER_SESSION_SUB_TYPE);
            int sessionsDone = sessionCount != null ? sessionCount : 0;

            int projectedKeyworkerSessions = getProjectedKeyworkerSessions(applicableAssignments, staffId, prisonId, fromDate, nextEndDate);
            final BigDecimal complianceRate = getComplianceRate(sessionsDone, projectedKeyworkerSessions);

            Integer entryCount = usageGroupedBySubType.get(KEYWORKER_ENTRY_SUB_TYPE);

            return KeyworkerStatsDto.builder()
                    .projectedKeyworkerSessions(projectedKeyworkerSessions)
                    .complianceRate(complianceRate)
                    .caseNoteEntryCount(entryCount != null ? entryCount : 0)
                    .caseNoteSessionCount(sessionsDone)
                    .build();
        }
        return KeyworkerStatsDto.builder()
                .projectedKeyworkerSessions(0)
                .complianceRate(PERCENT_OPERAND)
                .caseNoteEntryCount(0)
                .caseNoteSessionCount(0)
                .build();
    }

    public PrisonStatsDto getPrisonStats(String prisonId, LocalDate fromDate, LocalDate toDate) {
        Validate.notNull(prisonId,"prisonId");
        Validate.notNull(fromDate, "fromDate");
        Validate.notNull(toDate, "toDate");

        return PrisonStatsDto.builder().build();
    }

    private BigDecimal getComplianceRate(int sessionCount, int projectedKeyworkerSessions) {
        BigDecimal complianceRate = PERCENT_OPERAND;

        if (projectedKeyworkerSessions > 0)  {
            complianceRate = new BigDecimal(sessionCount).setScale(4, BigDecimal.ROUND_HALF_UP)
                    .divide(new BigDecimal(projectedKeyworkerSessions).setScale(4, BigDecimal.ROUND_HALF_UP)
                            .setScale(2, BigDecimal.ROUND_HALF_UP), RoundingMode.HALF_UP)
                    .multiply(PERCENT_OPERAND).setScale(2, BigDecimal.ROUND_HALF_UP);
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

}
