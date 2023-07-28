package uk.gov.justice.digital.hmpps.keyworker.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@ApiModel(description = "Released prisoner")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PrisonerCustodyStatusDto {

    @Schema(required = true, description = "Identifies prisoner.")
    @NotBlank
    private String offenderNo;

    @Schema(required = true, description = "Date and time the prisoner movement was created.")
    @NotNull
    private LocalDateTime createDateTime;

    @Schema(required = true, description = "Agency travelling from.")
    @NotBlank
    private String fromAgency;

    @Schema(required = true, description = "Agency travelling to.")
    @NotBlank
    private String toAgency;

    @Schema(required = true, description = "ADM(ission), REL(ease) or TRN(sfer).")
    @NotBlank
    private String movementType;

    @Schema(required = true, description = "IN or OUT.")
    @NotBlank
    private String directionCode;
}
