package uk.gov.justice.digital.hmpps.keyworker.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.NotBlank;

import java.time.LocalDate;

@ApiModel(description = "Offender Summary")

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PrisonerDetailDto {

    @ApiModelProperty(required = true, value = "The offender's unique offender number (aka NOMS Number in the UK).")
    @NotBlank
    private String offenderNo;

    private String title;

    @ApiModelProperty(required = true, value = "The offender's first name.")
    private String firstName;

    @ApiModelProperty(value = "The offender's middle name(s).")
    private String middleName;

    @ApiModelProperty(required = true, value = "The offender's last name.")
    private String lastName;

    private LocalDate dateOfBirth;

    private String gender;

    private String nationalities;

    @NotBlank
    private String currentlyInPrison;

    private Long latestBookingId;

    private String latestLocationId;

    private String latestLocation;

    private String pncNumber;

    private String croNumber;

    private String ethnicity;

    private String birthCountry;

    private String religion;

    private String convictedStatus;

    private String imprisonmentStatus;

    private LocalDate receptionDate;

    private String maritalStatus;
}
