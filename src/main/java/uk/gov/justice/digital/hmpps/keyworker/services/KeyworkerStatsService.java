package uk.gov.justice.digital.hmpps.keyworker.services;

import org.apache.commons.lang3.Validate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.digital.hmpps.keyworker.dto.CaseNoteUsageDto;
import uk.gov.justice.digital.hmpps.keyworker.dto.KeyworkerStatsDto;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class KeyworkerStatsService {
    private final NomisService nomisService;

    public KeyworkerStatsService(NomisService nomisService) {
        this.nomisService = nomisService;
    }

    public KeyworkerStatsDto getStatsFor(Long staffId, String prisonId, LocalDate fromDate, LocalDate toDate) {

        Validate.notNull(staffId, "staffId");
        Validate.notNull(prisonId,"prisonId");
        Validate.notNull(fromDate, "fromDate");
        Validate.notNull(toDate, "toDate");

        Integer caseNoteSessionCount = countCaseNotesForStaff(staffId, "KA", "KS", fromDate, toDate);
        Integer caseNoteEntryCount = countCaseNotesForStaff(staffId, "KA", "KA", fromDate, toDate);

        return KeyworkerStatsDto.builder()
                .projectedKeyworkerSessions(0)
                .complianceRate(0)
                .caseNoteEntryCount(caseNoteEntryCount)
                .caseNoteSessionCount(caseNoteSessionCount)
                .build();
    }

    private Integer countCaseNotesForStaff(long staffId, String caseNoteType, String caseNoteSubType, LocalDate fromDate, LocalDate toDate) {
        List<CaseNoteUsageDto> usageCounts =
                nomisService.getCaseNoteUsage(Arrays.asList(staffId), caseNoteType, caseNoteSubType, fromDate, toDate);

        Map<Long, Integer> groupedCounts =  usageCounts.stream()
                .collect(Collectors.groupingBy(CaseNoteUsageDto::getStaffId,
                        Collectors.summingInt(CaseNoteUsageDto::getNumCaseNotes)));

        Integer result = groupedCounts.get(staffId);

        if(result == null)
            return 0;

        return result;
    }
}
