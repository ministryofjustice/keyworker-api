package uk.gov.justice.digital.hmpps.keyworker.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
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

    @ApiModelProperty(required = true, value = "Key worker's allocation capacity.")
    @NotNull
    private Integer capacity;

    @ApiModelProperty(required = true, value = "Key worker's status.")
    @NotNull
    private KeyworkerStatus status;

    @ApiModelProperty(value = "Determines behaviour to apply to auto-allocation")
    private KeyworkerStatusBehaviour behaviour;

    @ApiModelProperty(required = false, value = "Date that the Key worker's status should be updated to Active")
    private LocalDate activeDate;
}
