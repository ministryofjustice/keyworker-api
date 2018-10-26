package uk.gov.justice.digital.hmpps.keyworker.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.NotBlank;

import java.math.BigDecimal;
import java.time.LocalDate;

@ApiModel(description = "KeyworkerStats")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class KeyworkerStatsDto {
    @ApiModelProperty(required = true, value = "Identifies the staff by ID.", example = "234233")
    @NotBlank
    private Long staffId;

    @ApiModelProperty(required = true, value = "Start date on which statistic results are based", example = "2018-07-01")
    @NotBlank
    private LocalDate fromDate;

    @ApiModelProperty(required = true, value = "End date on which statistic results are based", example = "2018-07-31")
    @NotBlank
    private LocalDate toDate;

    @ApiModelProperty(required = true, value = "Number of Session done based on case note type Key worker Activity, sub type Session", example = "24")
    @NotBlank
    private int caseNoteSessionCount;

    @ApiModelProperty(required = true, value = "Number of key worker entry case notes done based on case note type Key worker Activity, sub type Entry", example = "12")
    @NotBlank
    private int caseNoteEntryCount;

    @ApiModelProperty(required = true, value = "Number of projected key worker sessions that could have been done based on number of prisoners assigned to key worker and frequency of sessions set by this prison", example = "22")
    @NotBlank
    private int projectedKeyworkerSessions;

    @ApiModelProperty(required = true, value = "Percentage Compliance Rate of key worker session done over this time range", example = "87.5")
    @NotBlank
    private BigDecimal complianceRate;
}
