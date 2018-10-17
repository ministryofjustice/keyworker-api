package uk.gov.justice.digital.hmpps.keyworker.services;

import org.apache.commons.lang3.Validate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.digital.hmpps.keyworker.dto.CaseNoteUsageDto;
import uk.gov.justice.digital.hmpps.keyworker.dto.KeyworkerStatsDto;
import uk.gov.justice.digital.hmpps.keyworker.dto.PrisonStatsDto;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class KeyworkerStatsService {
    private final NomisService nomisService;

    public KeyworkerStatsService(NomisService nomisService) {
        this.nomisService = nomisService;
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

        Integer entryCount = usageGroupedBySubType.get("KA");
        Integer sessionCount = usageGroupedBySubType.get("KS");

        return KeyworkerStatsDto.builder()
                .projectedKeyworkerSessions(0)
                .complianceRate(0)
                .caseNoteEntryCount(entryCount != null ? entryCount : 0)
                .caseNoteSessionCount(sessionCount != null ? sessionCount :0)
                .build();
    }

    public PrisonStatsDto getPrisonStats(String prisonId, LocalDate startDate, LocalDate endDate) {
        return PrisonStatsDto.builder().build();
    }
}
