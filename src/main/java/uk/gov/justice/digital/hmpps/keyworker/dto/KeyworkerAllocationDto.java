package uk.gov.justice.digital.hmpps.keyworker.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.*;
import org.hibernate.validator.constraints.Length;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;


@ApiModel(description = "New Allocation")

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)

public class KeyworkerAllocationDto {

    @ApiModelProperty(required = true, value = "Offender Booking Id")
    @NotNull
    private Long bookingId;

    @ApiModelProperty(required = true, value = "Keyworker's staff Id")
    @NotNull
    private Long staffId;

    @ApiModelProperty(required = true, value = "Whether auto or manual")
    @NotNull
    private AllocationType type;

    @ApiModelProperty(value = "Allocation reason")
    @Length(max = 12)
    @Pattern(regexp = "\\w*")
    private String reason;
}
