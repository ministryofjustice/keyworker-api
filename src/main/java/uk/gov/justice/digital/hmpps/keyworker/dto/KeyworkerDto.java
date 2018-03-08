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

@ApiModel(description = "Key worker details")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class KeyworkerDto {

    @ApiModelProperty(required = true, value = "Unique staff identifier for Key worker.")
    @NotNull
    private Long staffId;

    @ApiModelProperty(required = true, value = "Key worker's first name.")
    @NotBlank
    private String firstName;

    @ApiModelProperty(required = true, value = "Key worker's last name.")
    @NotBlank
    private String lastName;

    @ApiModelProperty(value = "Key worker's email address.")
    private String email;

    @ApiModelProperty(value = "Identifier for Key worker image.")
    private Long thumbnailId;

    @ApiModelProperty(required = true, value = "Key worker's allocation capacity.")
    @NotNull
    private Integer capacity;

    @ApiModelProperty(required = true, value = "Number of offenders allocated to Key worker.")
    @NotNull
    private Integer numberAllocated;
}
