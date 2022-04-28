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
import java.util.SortedMap;

@ApiModel(description = "Prison Level Key worker Stats")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PrisonStatsDto {

    @Schema(required = true, description = "Requested start date for data set", example = "2018-04-01")
    @NotBlank
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate requestedFromDate;

    @Schema(required = true, description = "Requested end date for data set", example = "2018-04-31")
    @NotBlank
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate requestedToDate;

    @Schema(required = true, description = "Summary of Prison Statistics for the period requested.")
    @NotBlank
    private SummaryStatistic current;

    @Schema(required = true, description = "Summary of Prison Statistics for the previous period requested.")
    @NotBlank
    private SummaryStatistic previous;

    @Schema(description = "Date and percentage compliance key value pair of up to 1 years data before requestedToDate")
    private SortedMap<LocalDate, BigDecimal> complianceTimeline;

    @Schema(description = "Average Compliance for complianceTimeline", example = "75.3")
    private BigDecimal avgOverallCompliance;

    @Schema(description = "Date and percentage key value pair of up to 1 years data before requestedToDate")
    private SortedMap<LocalDate, Long> keyworkerSessionsTimeline;

    @Schema(description = "Average Key worker sessions for keyworkerSessionsTimeline", example = "502")
    private Integer avgOverallKeyworkerSessions;

}
