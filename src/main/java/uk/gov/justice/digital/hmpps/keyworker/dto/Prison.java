package uk.gov.justice.digital.hmpps.keyworker.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.*;
import org.hibernate.validator.constraints.NotBlank;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@ApiModel(description = "Prison")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@EqualsAndHashCode(of = "prisonId")
public class Prison {
    @ApiModelProperty(required = true, value = "Identifies prison.", example = "MDI", position = 0)
    @NotBlank
    private String prisonId;

    @ApiModelProperty(required = true, value = "Indicates that Key working is supported in this prison", example = "true", position = 1)
    @NotBlank
    private boolean supported;

    @ApiModelProperty(required = true, value = "Indicates that Key Worker data has been migrated to the Key Worker Service", example = "true", position = 2)
    @NotBlank
    private boolean migrated;

    @ApiModelProperty(required = true, value = "Indicates that this prison supports auto allocation of prisoner to key workers", example = "true", position = 3)
    @NotBlank
    private boolean autoAllocatedSupported;

    @ApiModelProperty(required = true, value = "Default auto allocation amount for staff in this prison.", example = "6", position = 4)
    @NotBlank
    private int capacityTier1;

    @ApiModelProperty(required = true, value = "Over allocation amount per staff member (max)", example = "9", position = 5)
    @NotBlank
    private int capacityTier2;

    @ApiModelProperty(required = true, value = "Frequency of Key working sessions in this prison", example = "1", position = 6)
    @NotBlank
    private int kwSessionFrequencyInWeeks;

    @ApiModelProperty(required = true, value = "Date and time migration of key workers was done for this prison", example = "2018-10-02T01:12:55.000", position = 7)
    @NotBlank
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime migratedDateTime;
}
