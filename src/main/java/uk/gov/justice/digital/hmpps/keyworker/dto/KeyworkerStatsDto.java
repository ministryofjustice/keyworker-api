package uk.gov.justice.digital.hmpps.keyworker.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.annotations.ApiModel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@ApiModel(description = "Key worker Stats")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class KeyworkerStatsDto {

    private Integer numActiveKeyworkers;
    private Integer percentagePrisonersWithKeyworker;
    private Integer numProjectedKeyworkerSessions;
    private Integer numRecordedKeyworkerSessions;
    private BigDecimal complianceRate;
    private Integer avgTimeReceptionToAllocation;
    private Integer avgTimeReceptionToKeyworkSession;

    private List<KeyworkerDataTimelineValue> complianceTimeline;
    private BigDecimal avgOverallCompliance;
    private List<KeyworkerDataTimelineValue> keyworkerSessionsTimeline;
    private Integer avgOverallKeyworkerSessions;

}
