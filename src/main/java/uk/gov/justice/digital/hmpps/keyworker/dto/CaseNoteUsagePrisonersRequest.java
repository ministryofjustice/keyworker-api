package uk.gov.justice.digital.hmpps.keyworker.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

@ApiModel(description = "Case Note Usage Request By Prisoners")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CaseNoteUsagePrisonersRequest {

    @ApiModelProperty(value = "The prison to filter by for case notes")
    private String agencyId;

    @ApiModelProperty(value = "List of offender Nos to look at case notes")
    private List<String> offenderNos;

    @ApiModelProperty(value = "Staff Id linked to these case notes (optional)")
    private Long staffId;

    @ApiModelProperty(required = true, value = "Case Note Type")
    @NotNull
    private String type;

    @ApiModelProperty(value = "Case Note Sub Type")
    private String subType;

    @ApiModelProperty(value = "Number of Months to look at")
    private Integer numMonths;

    @ApiModelProperty(value = "From Date to search from")
    private LocalDate fromDate;

    @ApiModelProperty(value = "To Date to search to")
    private LocalDate toDate;

}
