package uk.gov.justice.digital.hmpps.keyworker.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.NotBlank;
import uk.gov.justice.digital.hmpps.keyworker.model.AllocationReason;
import uk.gov.justice.digital.hmpps.keyworker.model.AllocationType;
import uk.gov.justice.digital.hmpps.keyworker.model.DeallocationReason;

import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;

@ApiModel(description = "New Key worker allocation")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class KeyworkerAllocationDto {

    @ApiModelProperty(required = true, value = "Identifies offender who is subject of allocation.")
    @NotBlank
    private String offenderNo;

    @ApiModelProperty(required = true, value = "Identifies Key worker who is subject of allocation.")
    @NotNull
    private Long staffId;

    @ApiModelProperty(required = true, value = "Agency where allocation is effective.")
    @NotBlank
    private String agencyId;

    @ApiModelProperty(required = true, value = "Type of allocation - auto or manual.")
    @NotNull
    private AllocationType allocationType;

    @ApiModelProperty(required = true, value = "Reason for allocation.")
    @NotNull
    private AllocationReason allocationReason;

    @ApiModelProperty(value = "Reason for de-allocation.")
    private DeallocationReason deallocationReason;
}
