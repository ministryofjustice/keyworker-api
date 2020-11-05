package uk.gov.justice.digital.hmpps.keyworker.dto

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty
import lombok.*
import javax.validation.constraints.NotNull

@ApiModel(description = "Caseload Update")
@JsonInclude(JsonInclude.Include.NON_NULL)
@Builder
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode
@Data
data class CaseloadUpdate(
        @ApiModelProperty(required = true, value = "Caseload", example = "MDI") val caseload: String,
        @ApiModelProperty(required = true, value = "Number of users enabled to access API", example = "5", position = 2) val numUsersEnabled: Int = 0
)