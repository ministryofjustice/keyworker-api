package uk.gov.justice.digital.hmpps.keyworker.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;
import java.util.Map;

@ApiModel(description = "Prison Key worker Stats")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class KeyworkerStatSummary {

    @Schema(required = true, description = "Summary of all prisons specified")
    @NotBlank
    private PrisonStatsDto summary;

    @Schema(required = true, description = "Individual prison stats")
    @NotBlank
    private Map<String, PrisonStatsDto> prisons;
}
