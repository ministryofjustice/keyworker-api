package uk.gov.justice.digital.hmpps.keyworker.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@ApiModel(description = "Prisoner Detail")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@ToString(of = {"offenderNo"})
public class PrisonerDetail implements Serializable {

    @Schema(required = true, description = "Identifies prisoner.")
    @NotBlank
    private String offenderNo;

    @Schema(required = true, description = "The prisoner's Title")
    private String title;

    @Schema(required = true, description = "The prisoner's Suffix")
    private String suffix;

    @Schema(required = true, description = "The prisoner's first name.")
    @NotBlank
    private String firstName;

    @Schema(required = true, description = "The prisoner's middle names.")
    private String middleNames;

    @Schema(required = true, description = "The prisoner's last name.")
    @NotBlank
    private String lastName;

    @Schema(required = true, description = "The prisoner's date of birth")
    @NotNull
    private LocalDate dateOfBirth;

    @Schema(required = true, description = "The prisoner's gender")
    @NotBlank
    private String gender;

    @Schema(required = true, description = "Indicate Y if in prison")
    @NotBlank
    private String currentlyInPrison;

    @Schema(required = true, description = "Latest booking id")
    private Long latestBookingId;

    @Schema(required = true, description = "Latest Location Id")
    private String latestLocationId;

    @Schema(required = true, description = "Latest location")
    private String latestLocation;

    @Schema(required = true, description = "Last Internal location")
    private String internalLocation;

    @Schema(required = true, description = "Current Imprisonment Status")
    private String imprisonmentStatus;

    @Schema(required = true, description = "Date received into prison")
    private LocalDate receptionDate;

    public boolean isInPrison() {
        return "Y".equals(currentlyInPrison);
    }
}
