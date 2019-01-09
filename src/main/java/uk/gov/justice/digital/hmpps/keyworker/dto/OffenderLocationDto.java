package uk.gov.justice.digital.hmpps.keyworker.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.*;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

@ApiModel(description = "Offender Summary")

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@ToString(exclude={"firstName","lastName","middleName","aliases"})
public class OffenderLocationDto {

    @ApiModelProperty(required = true, value = "The offender's unique offender number (aka NOMS Number in the UK).")
    @NotBlank
    private String offenderNo;

    @ApiModelProperty(required = true, value = "A unique booking id.")
    @NotNull
    private Long bookingId;

    @ApiModelProperty(required = true, value = "The offender's first name.")
    @NotBlank
    private String firstName;

    @ApiModelProperty(value = "The offender's middle name(s).")
    private String middleName;

    @ApiModelProperty(required = true, value = "The offender's last name.")
    @NotBlank
    private String lastName;

    private LocalDate dateOfBirth;

    @ApiModelProperty(value = "Agency Id (if known)")
    private String agencyId;

    @ApiModelProperty(value = "Internal location id (if known)")
    private Long assignedLivingUnitId;

    @ApiModelProperty(value = "Internal location description (if known)")
    private String assignedLivingUnitDesc;

    private Long facialImageId;

    private String assignedOfficerUserId;

    private List<String> aliases;

    private String iepLevel;
}
