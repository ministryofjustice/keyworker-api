package uk.gov.justice.digital.hmpps.keyworker.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.*;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.time.LocalDate;

@ApiModel(description = "Prisoner Detail")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@ToString(of = { "offenderNo" })
public class PrisonerDetail implements Serializable {

    @ApiModelProperty(required = true, value = "Identifies prisoner.")
    @NotBlank
    private String offenderNo;

    @ApiModelProperty(required = true, value = "The prisoner's Title")
    private String title;

    @ApiModelProperty(required = true, value = "The prisoner's Suffix")
    private String suffix;

    @ApiModelProperty(required = true, value = "The prisoner's first name.")
    @NotBlank
    private String firstName;

    @ApiModelProperty(required = true, value = "The prisoner's middle names.")
    private String middleNames;

    @ApiModelProperty(required = true, value = "The prisoner's last name.")
    @NotBlank
    private String lastName;

    @ApiModelProperty(required = true, value = "The prisoner's date of birth")
    @NotNull
    private LocalDate dateOfBirth;

    @ApiModelProperty(required = true, value = "The prisoner's gender")
    @NotBlank
    private String gender;

    @ApiModelProperty(required = true, value = "Indicate Y if in prison")
    @NotBlank
    private String currentlyInPrison;

    @ApiModelProperty(required = true, value = "Latest booking id")
    private Long latestBookingId;

    @ApiModelProperty(required = true, value = "Latest Location Id")
    private String latestLocationId;

    @ApiModelProperty(required = true, value = "Latest location")
    private String latestLocation;

    @ApiModelProperty(required = true, value = "Last Internal location")
    private String internalLocation;

    @ApiModelProperty(required = true, value = "Current Imprisonment Status")
    private String imprisonmentStatus;

    @ApiModelProperty(required = true, value = "Date received into prison")
    private LocalDate receptionDate;
}
