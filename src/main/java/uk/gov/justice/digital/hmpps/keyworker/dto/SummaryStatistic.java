package uk.gov.justice.digital.hmpps.keyworker.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

@ApiModel(description = "SummaryStatistic")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SummaryStatistic {
    @Schema(required = true, description = "Starting date for the set of summary data", example = "2018-06-01")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dataRangeFrom;

    @Schema(required = true, description = "End date for the set of summary data", example = "2018-07-30")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dataRangeTo;

    @Schema(required = true, description = "Average number of prisoners assigned a key worker over this time range", example = "423")
    private Integer numPrisonersAssignedKeyWorker;

    @Schema(required = true, description = "Average total number of prisoners in the prisons over this time range", example = "600")
    private Integer totalNumPrisoners;

    @Schema(required = true, description = "Average total number of eligible prisoners in the prisons over this time range", example = "600")
    private Integer totalNumEligiblePrisoners;

    @Schema(required = true, description = "Average number of Key Working Sessions done over this time range", example = "354")
    private Integer numberKeyWorkerSessions;

    @Schema(required = true, description = "Average number of Key Worker Entries made over this time range", example = "232")
    private Integer numberKeyWorkerEntries;

    @Schema(required = true, description = "Average number of Active Key Workers over this time range", example = "320")
    private Integer numberOfActiveKeyworkers;

    @Schema(required = true, description = "Average percentage of Prisoners who have been assigned a Key Worker over this time range", example = "87.2")
    private BigDecimal percentagePrisonersWithKeyworker;

    @Schema(required = true, description = "Average number of projected Key Worker sessions that could be done based on available key workers and frequency of sessions (e.g 1/week)", example = "501")
    private Integer numProjectedKeyworkerSessions;

    @Schema(required = true, description = "Overall compliance rate for this time period", example = "87.5")
    private BigDecimal complianceRate;

    @Schema(required = true, description = "Average number of days between a prisoner entering this prison and being allocated a key worker.",
        example = "5")
    private Integer avgNumDaysFromReceptionToAllocationDays;

    @Schema(required = true, description = "Average number of days between a prisoner entering this prison and receiving a session from key worker",
           example = "10")
    private Integer avgNumDaysFromReceptionToKeyWorkingSession;
}
