package uk.gov.justice.digital.hmpps.keyworker.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.NotBlank;

import java.util.Map;

@ApiModel(description = "Prison Key worker Stats")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class KeyworkerStatSummary {

    @ApiModelProperty(required = true, value = "Summary of all prisons specified", position = 1)
    @NotBlank
    private PrisonStatsDto summary;

    @ApiModelProperty(required = true, value = "Individual prison stats", position = 2)
    @NotBlank
    private Map<String, PrisonStatsDto> prisons;
}
