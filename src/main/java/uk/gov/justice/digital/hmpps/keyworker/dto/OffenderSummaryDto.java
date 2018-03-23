package uk.gov.justice.digital.hmpps.keyworker.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.NotBlank;

import javax.validation.constraints.NotNull;

@ApiModel(description = "Offender Summary")

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OffenderSummaryDto {

    @ApiModelProperty(required = true, value = "A unique booking id.")
    @NotNull
    private Long bookingId;

    @ApiModelProperty(required = true, value = "The offender's unique offender number (aka NOMS Number in the UK).")
    @NotBlank
    private String offenderNo;

    @ApiModelProperty(value = "A code representing the offender's title (from TITLE reference domain).")
    private String title;

    @ApiModelProperty(value = "A code representing a suffix that is applied to offender's name (from SUFFIX reference domain).")
    private String suffix;

    @ApiModelProperty(required = true, value = "The offender's first name.")
    @NotBlank
    private String firstName;

    @ApiModelProperty(value = "The offender's middle name(s).")
    private String middleNames;

    @ApiModelProperty(required = true, value = "The offender's last name.")
    @NotBlank
    private String lastName;

    @ApiModelProperty(value = "Set to Y or N to indicate if the person is currently in prison. If not set, status is not known.")
    private String currentlyInPrison;

    @ApiModelProperty(value = "Agency Id (if known)")
    private String agencyId;

    @ApiModelProperty(value = "Agency description (if known)")
    private String agencyLocationDesc;

    @ApiModelProperty(value = "Internal location id (if known)")
    private String internalLocationId;

    @ApiModelProperty(value = "Internal location description (if known)")
    private String internalLocationDesc;
}
