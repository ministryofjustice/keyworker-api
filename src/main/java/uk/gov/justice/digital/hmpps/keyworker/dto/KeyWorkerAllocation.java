package uk.gov.justice.digital.hmpps.keyworker.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.*;
import org.springframework.format.annotation.DateTimeFormat;
import uk.gov.justice.digital.hmpps.keyworker.model.AllocationType;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;

@ApiModel(description = "Offender allocation history details")

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@ToString(exclude={"firstName","lastName"})
public class KeyWorkerAllocation {

    @ApiModelProperty(required = true, value = "Id of offender allocation.")
    @NotNull
    private Long offenderKeyworkerId;

    @ApiModelProperty(required = true, value = "The offender's Key worker staff Id.")
    @NotNull
    private Long staffId;

    @ApiModelProperty(required = true, value = "Key worker's first name.")
    @NotBlank
    private String firstName;

    @ApiModelProperty(required = true, value = "Key worker's last name.")
    @NotBlank
    private String lastName;

    @ApiModelProperty(required = true, value = "Prison Id where allocation is effective.")
    @NotBlank
    private String prisonId;

    @ApiModelProperty(required = true, value = "The date and time of the allocation.")
    @NotNull
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime assigned;

    @ApiModelProperty(required = false, value = "The date and time of deallocation.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime expired;

    @ApiModelProperty(required = true, value = "The user who created the allocation.")
    @NotBlank
    private StaffUser userId;

    @ApiModelProperty(required = true, value = "Whether allocation is active.")
    @NotBlank
    private boolean active;

    @ApiModelProperty(required = true, value = "Type of allocation - auto or manual.")
    @NotNull
    private AllocationType allocationType;

    @ApiModelProperty(required = true, value = "Reason for allocation.")
    @NotNull
    private String allocationReason;

    @ApiModelProperty(value = "Reason for de-allocation.")
    private String deallocationReason;

    @ApiModelProperty(required = false, value = "The date and time of creation.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime creationDateTime;

    @ApiModelProperty(required = true, value = "The user who created the allocation.")
    @NotBlank
    private StaffUser createdByUser;

    @ApiModelProperty(required = false, value = "Last date and time of modification.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime modifyDateTime;

    @ApiModelProperty(required = true, value = "The user who last modified the allocation.")
    private StaffUser lastModifiedByUser;

}
