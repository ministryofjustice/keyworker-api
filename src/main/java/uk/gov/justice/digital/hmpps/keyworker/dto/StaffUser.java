package uk.gov.justice.digital.hmpps.keyworker.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@ApiModel(description = "Staff Detail")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@EqualsAndHashCode()
@ToString(exclude = {"firstName", "lastName"})
public class StaffUser {

    @Schema(required = true, description = "Unique staff identifier")
    @NotNull
    private Long staffId;

    @Schema(required = true, description = "Staff first name.")
    @NotBlank
    private String firstName;

    @Schema(required = true, description = "Staff last name.")
    @NotBlank
    private String lastName;

    @Schema(description = "Staff username")
    @NotBlank
    private String username;
}
