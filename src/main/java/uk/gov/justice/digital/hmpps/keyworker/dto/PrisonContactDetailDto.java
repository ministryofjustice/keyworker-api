package uk.gov.justice.digital.hmpps.keyworker.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;

@ApiModel(description = "Prison Contact Detail")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@EqualsAndHashCode(of = "agencyId")
public class PrisonContactDetailDto {

    @Schema(required = true, description = "Identifies agency (prison).", example = "MDI")
    @NotBlank
    private String agencyId;

    @NotBlank
    private String addressType;

    @NotBlank
    private String premise;

    @NotBlank
    private String locality;

    @NotBlank
    private String city;

    @NotBlank
    private String country;

    @NotBlank
    private String postCode;

}
