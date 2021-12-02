package uk.gov.justice.digital.hmpps.keyworker.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
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
    @ApiModelProperty(required = true, value = "Starting date for the set of summary data", example = "2018-06-01", position = 0)
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dataRangeFrom;

    @ApiModelProperty(required = true, value = "End date for the set of summary data", example = "2018-07-30", position = 1)
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dataRangeTo;

    @ApiModelProperty(required = true, value = "Average number of prisoners assigned a key worker over this time range", example = "423", position = 2)
    private Integer numPrisonersAssignedKeyWorker;

    @ApiModelProperty(required = true, value = "Average total number of prisoners in the prisons over this time range", example = "600", position = 3)
    private Integer totalNumPrisoners;

    @ApiModelProperty(required = true, value = "Average total number of eligible prisoners in the prisons over this time range", example = "600", position = 3)
    private Integer totalNumEligiblePrisoners;

    @ApiModelProperty(required = true, value = "Average number of Key Working Sessions done over this time range", example = "354", position = 4)
    private Integer numberKeyWorkerSessions;

    @ApiModelProperty(required = true, value = "Average number of Key Worker Entries made over this time range", example = "232", position = 5)
    private Integer numberKeyWorkerEntries;

    @ApiModelProperty(required = true, value = "Average number of Active Key Workers over this time range", example = "320", position = 6)
    private Integer numberOfActiveKeyworkers;

    @ApiModelProperty(required = true, value = "Average percentage of Prisoners who have been assigned a Key Worker over this time range", example = "87.2", position = 7)
    private BigDecimal percentagePrisonersWithKeyworker;

    @ApiModelProperty(required = true, value = "Average number of projected Key Worker sessions that could be done based on available key workers and frequency of sessions (e.g 1/week)", example = "501", position = 8)
    private Integer numProjectedKeyworkerSessions;

    @ApiModelProperty(required = true, value = "Overall compliance rate for this time period", example = "87.5", position = 9)
    private BigDecimal complianceRate;

    @ApiModelProperty(required = true, value = "Average number of days between a prisoner entering this prison and being allocated a key worker.",
            notes = "This only included prisoners who entered the after key working began in this prison", example = "5", position = 10)
    private Integer avgNumDaysFromReceptionToAllocationDays;

    @ApiModelProperty(required = true, value = "Average number of days between a prisoner entering this prison and receiving a session from key worker",
            notes = "This only included prisoners who entered the after key working began in this prison", example = "10", position = 11)
    private Integer avgNumDaysFromReceptionToKeyWorkingSession;
}
