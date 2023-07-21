package uk.gov.justice.digital.hmpps.keyworker.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@ApiModel(description = "Case Note Usage Request By Prisoners")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CaseNoteUsagePrisonersRequest {

    @Schema(description = "The prison to filter by for case notes")
    private String agencyId;

    @Schema(description = "List of offender Nos to look at case notes")
    private List<String> offenderNos;

    @Schema(description = "Staff Id linked to these case notes (optional)")
    private Long staffId;

    @Schema(required = true, description = "Case Note Type")
    @NotNull
    private String type;

    @Schema(description = "Case Note Sub Type")
    private String subType;

    @Schema(description = "Number of Months to look at")
    private Integer numMonths;

    @Schema(description = "From Date to search from")
    private LocalDate fromDate;

    @Schema(description = "To Date to search to")
    private LocalDate toDate;

}
