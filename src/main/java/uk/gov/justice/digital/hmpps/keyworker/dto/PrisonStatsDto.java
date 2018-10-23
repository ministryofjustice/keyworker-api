package uk.gov.justice.digital.hmpps.keyworker.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.annotations.ApiModel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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

    private String prisonId;
    private LocalDate requestedFromDate;
    private LocalDate requestedToDate;

    private SummaryStatistic current;
    private SummaryStatistic previous;

    private SortedMap<LocalDate, Double> complianceTimeline;
    private BigDecimal avgOverallCompliance;
    private SortedMap<LocalDate, Double> keyworkerSessionsTimeline;
    private Integer avgOverallKeyworkerSessions;

}
