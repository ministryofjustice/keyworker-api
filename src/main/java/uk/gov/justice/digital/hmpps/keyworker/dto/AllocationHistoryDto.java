package uk.gov.justice.digital.hmpps.keyworker.dto;


import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AllocationHistoryDto {

    @Schema(required = true, description = "offender No.")
    @NotNull
    private String offenderNo;

    @Schema(required = true, description = "The offender's Key worker staff Id.")
    @NotNull
    private Long staffId;

    @Schema(required = true, description = "Agency Id where allocation is effective.")
    @NotBlank
    private String agencyId;

    @Schema(required = true, description = "The date and time of the allocation.")
    @NotNull
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime assigned;

    @Schema(required = false, description = "The date and time of deallocation.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime expired;

    @Schema(required = true, description = "The username who created the allocation.")
    @NotBlank
    private String userId;

    @Schema(required = true, description = "Whether allocation is active. Y=true")
    @NotBlank
    private String active;

    @Schema(required = false, description = "The date and time of creation.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime created;

    @Schema(required = true, description = "The username who created the allocation.")
    @NotBlank
    private String createdBy;

    @Schema(required = false, description = "Last date and time of modification.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime modified;

    @Schema(required = true, description = "The username who last modified the allocation.")
    private String modifiedBy;

}
