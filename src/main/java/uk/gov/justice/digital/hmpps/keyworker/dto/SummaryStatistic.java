package uk.gov.justice.digital.hmpps.keyworker.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.annotations.ApiModel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@ApiModel(description = "SummaryStatistic")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SummaryStatistic {
    private Integer numPrisonersAssignedKeyWorker;
    private Integer totalNumPrisoners;
    private Integer numberKeyWorkeringSessions;
    private Integer numberKeyWorkerEntries;
    private Integer numberOfActiveKeyworkers;

    private Integer percentagePrisonersWithKeyworker;
    private Integer numProjectedKeyworkerSessions;
    private BigDecimal complianceRate;

    private Integer avgNumDaysFromReceptionToAlliocationDays;
    private Integer avgNumDaysFromReceptionToKeyWorkingSession;
}
