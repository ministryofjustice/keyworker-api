package uk.gov.justice.digital.hmpps.keyworker.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.*;
import org.hibernate.validator.constraints.NotBlank;

import javax.validation.constraints.NotNull;

@ApiModel(description = "Staff Detail")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@EqualsAndHashCode()
@ToString(exclude={"firstName","lastName"})
public class StaffUser {

    @ApiModelProperty(required = true, value = "Unique staff identifier")
    @NotNull
    private Long staffId;

    @ApiModelProperty(required = true, value = "Staff first name.")
    @NotBlank
    private String firstName;

    @ApiModelProperty(required = true, value = "Staff last name.")
    @NotBlank
    private String lastName;

    @ApiModelProperty(value = "Staff username")
    @NotBlank
    private String username;
}
