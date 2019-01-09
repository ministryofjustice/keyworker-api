package uk.gov.justice.digital.hmpps.keyworker.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotEmpty;
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

    @ApiModelProperty(required = true, value = "List of offender Nos to look at case notes")
    @NotEmpty
    private List<String> offenderNos;

    @ApiModelProperty(required = true, value = "Staff Id linked to these case notes (optional)")
    private Long staffId;

    @ApiModelProperty(required = true, value = "Case Note Type")
    @NotNull
    private String type;

    @ApiModelProperty(required = true, value = "Case Note Sub Type")
    private String subType;

    @ApiModelProperty(required = true, value = "Number of Months to look at")
    private Integer numMonths;

    @ApiModelProperty(required = true, value = "From Date to search from")
    private LocalDate fromDate;

    @ApiModelProperty(required = true, value = "To Date to search to")
    private LocalDate toDate;

}
