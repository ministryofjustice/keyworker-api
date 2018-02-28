package uk.gov.justice.digital.hmpps.keyworker.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.*;
import org.hibernate.validator.constraints.NotBlank;


@ApiModel(description = "Keyworker Details")

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)

public class KeyworkerDto {

    @ApiModelProperty(required = true, value = "Staff Id")
    private long staffId;

    @ApiModelProperty(required = true, value = "")
    @NotBlank
    private String firstName;

    @ApiModelProperty(required = true, value = "")
    @NotBlank
    private String lastName;

    @ApiModelProperty(value = "")
    private String email;

    @ApiModelProperty(value = "")
    private Long thumbnailId;

    @ApiModelProperty(required = true, value = "Current number allocated")
    private int numberAllocated;
}
