package uk.gov.justice.digital.hmpps.keyworker.services;

import org.apache.commons.lang3.Validate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.digital.hmpps.keyworker.dto.CaseNoteUsageDto;
import uk.gov.justice.digital.hmpps.keyworker.dto.KeyworkerStatsDto;
import uk.gov.justice.digital.hmpps.keyworker.dto.Prison;
import uk.gov.justice.digital.hmpps.keyworker.dto.PrisonStatsDto;
import uk.gov.justice.digital.hmpps.keyworker.model.OffenderKeyworker;
import uk.gov.justice.digital.hmpps.keyworker.repository.OffenderKeyworkerRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.LongSummaryStatistics;
import java.util.Map;
import java.util.stream.Collectors;

import static java.time.temporal.ChronoUnit.DAYS;
import static java.time.temporal.ChronoUnit.WEEKS;

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

        List<CaseNoteUsageDto> usageCounts =
                nomisService.getCaseNoteUsage(Collections.singletonList(staffId), "KA", null, fromDate, toDate);

        Map<String, Integer> usageGroupedBySubType =  usageCounts.stream()
                .collect(Collectors.groupingBy(CaseNoteUsageDto::getCaseNoteSubType,
                        Collectors.summingInt(CaseNoteUsageDto::getNumCaseNotes)));


        Integer sessionCount = usageGroupedBySubType.get("KS");
        int sessionsDone = sessionCount != null ? sessionCount : 0;

        int projectedKeyworkerSessions = getProjectedKeyworkerSessions(staffId, prisonId, fromDate, toDate);
        final BigDecimal complianceRate = getComplianceRate(sessionsDone, projectedKeyworkerSessions);

        Integer entryCount = usageGroupedBySubType.get("KA");

        return KeyworkerStatsDto.builder()
                .projectedKeyworkerSessions(projectedKeyworkerSessions)
                .complianceRate(complianceRate)
                .caseNoteEntryCount(entryCount != null ? entryCount : 0)
                .caseNoteSessionCount(sessionsDone)
                .build();
    }

    public PrisonStatsDto getPrisonStats(String prisonId, LocalDate startDate, LocalDate endDate) {
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

    private int getProjectedKeyworkerSessions(Long staffId, String prisonId, LocalDate fromDate, LocalDate toDate) {
        final LocalDateTime nextEndDate = toDate.atStartOfDay().plusDays(1);
        List<OffenderKeyworker> allAllocations = repository.findByStaffIdAndPrisonId(staffId, prisonId);
        final Map<Long, LongSummaryStatistics> kwResults = allAllocations.stream()
                .filter(kw ->
                        kw.getAssignedDateTime().compareTo(nextEndDate) < 0 &&
                                (kw.getExpiryDateTime() == null || kw.getExpiryDateTime().compareTo(fromDate.atStartOfDay()) >= 0)
                ).collect(
                        Collectors.groupingBy(
                                OffenderKeyworker::getStaffId, Collectors.summarizingLong(OffenderKeyworker::getDaysAllocated)
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
