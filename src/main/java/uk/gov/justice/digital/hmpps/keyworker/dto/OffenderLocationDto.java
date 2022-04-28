package uk.gov.justice.digital.hmpps.keyworker.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.LocalDate;

@ApiModel(description = "Offender Summary")

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@ToString(exclude = {"firstName", "lastName", "middleName"})
public class OffenderLocationDto {

    @Schema(required = true, description = "The offender's unique offender number (aka NOMS Number in the UK).")
    @NotBlank
    private String offenderNo;

    @Schema(required = true, description = "A unique booking id.")
    @NotNull
    private Long bookingId;

    @Schema(required = true, description = "The offender's first name.")
    @NotBlank
    private String firstName;

    @Schema(description = "The offender's middle name(s).")
    private String middleName;

    @Schema(required = true, description = "The offender's last name.")
    @NotBlank
    private String lastName;

    private LocalDate dateOfBirth;

    @Schema(description = "Agency Id (if known)")
    private String agencyId;

    @Schema(description = "Internal location id (if known)")
    private Long assignedLivingUnitId;

    @Schema(description = "Internal location description (if known)")
    private String assignedLivingUnitDesc;
}
