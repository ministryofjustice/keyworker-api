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

import javax.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.LocalDate;

@ApiModel(description = "KeyworkerStats")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class KeyworkerStatsDto {
    @Schema(required = true, description = "Identifies the staff by ID.", example = "234233")
    @NotBlank
    private Long staffId;

    @Schema(required = true, description = "Start date on which statistic results are based", example = "2018-07-01")
    @NotBlank
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate fromDate;

    @Schema(required = true, description = "End date on which statistic results are based", example = "2018-07-31")
    @NotBlank
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate toDate;

    @Schema(required = true, description = "Number of Session done based on case note type Key worker Activity, sub type Session", example = "24")
    @NotBlank
    private int caseNoteSessionCount;

    @Schema(required = true, description = "Number of key worker entry case notes done based on case note type Key worker Activity, sub type Entry", example = "12")
    @NotBlank
    private int caseNoteEntryCount;

    @Schema(required = true, description = "Number of projected key worker sessions that could have been done based on number of prisoners assigned to key worker and frequency of sessions set by this prison", example = "22")
    @NotBlank
    private int projectedKeyworkerSessions;

    @Schema(required = true, description = "Percentage Compliance Rate of key worker session done over this time range", example = "87.5")
    @NotBlank
    private BigDecimal complianceRate;
}
