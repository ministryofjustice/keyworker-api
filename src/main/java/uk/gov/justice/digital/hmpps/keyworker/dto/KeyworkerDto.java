package uk.gov.justice.digital.hmpps.keyworker.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.validator.constraints.NotBlank;
import uk.gov.justice.digital.hmpps.keyworker.model.KeyworkerStatus;

import javax.validation.constraints.NotNull;
import java.util.Arrays;

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

    @ApiModelProperty(value = "Key worker's schedule type.")
    private String scheduleType;

    @ApiModelProperty(value = "Key worker's agency Id.")
    private String agencyId;

    @ApiModelProperty(value = "Key worker's agency description.")
    private String agencyDescription;

    @ApiModelProperty(value = "Key worker's status.")
    private KeyworkerStatus status;

    @ApiModelProperty(value = "Key worker is eligible for auto allocation.")
    private Boolean autoAllocationAllowed;

    /**
     * @return fullname (last name first name in lower case)
     */
    public String getFullName() {
        return StringUtils.lowerCase(StringUtils.join(Arrays.asList(lastName, firstName), " "));
    }
}
