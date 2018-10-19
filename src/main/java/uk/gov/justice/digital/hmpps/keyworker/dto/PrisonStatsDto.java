package uk.gov.justice.digital.hmpps.keyworker.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.annotations.ApiModel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.tuple.ImmutablePair;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@ApiModel(description = "Prison Level Key worker Stats")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PrisonStatsDto {

    private String prisonId;
    private LocalDate fromDate;
    private LocalDate toDate;

    private SummaryStatistic current;
    private SummaryStatistic previous;

    private List<ImmutablePair<LocalDate, BigDecimal>> complianceTimeline;
    private BigDecimal avgOverallCompliance;
    private List<ImmutablePair<LocalDate, Long>> keyworkerSessionsTimeline;
    private Integer avgOverallKeyworkerSessions;

}
