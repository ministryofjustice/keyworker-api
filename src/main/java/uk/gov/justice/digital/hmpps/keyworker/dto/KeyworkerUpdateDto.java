package uk.gov.justice.digital.hmpps.keyworker.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.justice.digital.hmpps.keyworker.model.KeyworkerStatus;

import javax.validation.constraints.NotNull;
import java.time.LocalDate;

@ApiModel(description = "Key worker details")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)

public class KeyworkerUpdateDto {

    @Schema(required = true, description = "Key worker's allocation capacity.")
    @NotNull
    private Integer capacity;

    @Schema(required = true, description = "Key worker's status.")
    @NotNull
    private KeyworkerStatus status;

    @Schema(description = "Determines behaviour to apply to auto-allocation")
    private KeyworkerStatusBehaviour behaviour;

    @Schema(required = false, description = "Date that the Key worker's status should be updated to Active")
    private LocalDate activeDate;
}
