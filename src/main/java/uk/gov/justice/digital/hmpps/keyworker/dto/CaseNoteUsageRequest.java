package uk.gov.justice.digital.hmpps.keyworker.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@ApiModel(description = "Case Note Usage Request")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CaseNoteUsageRequest {

    @Schema(required = true, description = "List of staff Id to look at case notes")
    @NotEmpty
    private List<Long> staffIds;

    @Schema(required = true, description = "Case Note Type")
    @NotNull
    private String type;

    @Schema(required = true, description = "Case Note Sub Type")
    private String subType;

    @Schema(required = true, description = "Number of Months to look at")
    private Integer numMonths;

    @Schema(required = true, description = "From Date to search from")
    private LocalDate fromDate;

    @Schema(required = true, description = "To Date to search to")
    private LocalDate toDate;

}
