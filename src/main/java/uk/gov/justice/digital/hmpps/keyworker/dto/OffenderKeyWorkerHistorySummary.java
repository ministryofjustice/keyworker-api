package uk.gov.justice.digital.hmpps.keyworker.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;

@ApiModel(description = "Offender allocation history summary")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OffenderKeyWorkerHistorySummary {

    @Schema(required = true, description = "Identifies prisoner.")
    @NotBlank
    private String offenderNo;

    @Schema(required = true, description = "Whether this prisoner has ever had a keyworker allocated.")
    @NotBlank
    private boolean hasHistory;
}
