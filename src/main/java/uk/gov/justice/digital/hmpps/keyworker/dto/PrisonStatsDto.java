package uk.gov.justice.digital.hmpps.keyworker.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
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

    @ApiModelProperty(required = true, value = "Requested start date for data set", example = "2018-04-01", dataType = "LocalDate", position = 1)
    @NotBlank
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate requestedFromDate;

    @ApiModelProperty(required = true, value = "Requested end date for data set", example = "2018-04-31", dataType = "LocalDate", position = 2)
    @NotBlank
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate requestedToDate;

    @ApiModelProperty(required = true, value = "Summary of Prison Statistics for the period requested.", dataType = "SummaryStatistic", position = 3)
    @NotBlank
    private SummaryStatistic current;

    @ApiModelProperty(required = true, value = "Summary of Prison Statistics for the previous period requested.", dataType = "SummaryStatistic", position = 4)
    @NotBlank
    private SummaryStatistic previous;

    @ApiModelProperty(value = "Date and percentage compliance key value pair of up to 1 years data before requestedToDate", position = 5)
    private SortedMap<LocalDate, BigDecimal> complianceTimeline;

    @ApiModelProperty(value = "Average Compliance for complianceTimeline", example = "75.3", position = 6)
    private BigDecimal avgOverallCompliance;

    @ApiModelProperty(value = "Date and percentage key value pair of up to 1 years data before requestedToDate", dataType = "Map<LocalDate, Long>", position = 7)
    private SortedMap<LocalDate, Long> keyworkerSessionsTimeline;

    @ApiModelProperty(value = "Average Key worker sessions for keyworkerSessionsTimeline", example = "502", position = 8)
    private Integer avgOverallKeyworkerSessions;

}
