package uk.gov.justice.digital.hmpps.keyworker.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.*;
import org.hibernate.validator.constraints.NotBlank;
import org.springframework.format.annotation.DateTimeFormat;

import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;

@ApiModel(description = "Key worker allocation details")

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)

public class KeyworkerAllocationDetailsDto {

    @ApiModelProperty(required = true, value = "Offender Booking Id")
    @NotNull
    private Long bookingId;

    @ApiModelProperty(required = true, value = "Offender Unique Reference")
    @NotBlank
    private String offenderNo;

    @ApiModelProperty(required = true, value = "First Name")
    @NotBlank
    private String firstName;

    @ApiModelProperty(value = "Middle Name(s)")
    private String middleNames;

    @ApiModelProperty(required = true, value = "Last Name")
    @NotBlank
    private String lastName;

    @ApiModelProperty(required = true, value = "The key worker's Staff Id")
    @NotNull
    private Long staffId;

    @ApiModelProperty(required = true, value = "Agency Id")
    @NotBlank
    private String agencyId;

    @ApiModelProperty(required = true, value = "Date and time of the allocation")
    @NotNull
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime assigned;

    @ApiModelProperty(required = true, value = "A")
    @NotNull
    private AllocationType allocationType;

    @ApiModelProperty(required = true, value = "Description of the location within the prison")
    @NotBlank
    private String internalLocationDesc;
}
