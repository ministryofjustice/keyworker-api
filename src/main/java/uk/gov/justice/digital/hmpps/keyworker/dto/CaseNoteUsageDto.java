package uk.gov.justice.digital.hmpps.keyworker.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@ApiModel(description = "Case Note Usage")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@EqualsAndHashCode(of = {"staffId", "caseNoteType", "caseNoteSubType"})
@ToString
public class CaseNoteUsageDto {

    @Schema(required = true, description = "Unique staff identifier for Key worker.")
    @NotNull
    private Long staffId;

    @Schema(required = true, description = "Case Note Type")
    @NotNull
    private String caseNoteType;

    @Schema(required = true, description = "Case Note Sub Type")
    @NotNull
    private String caseNoteSubType;

    @Schema(required = true, description = "Number of case notes of this type")
    @NotNull
    private Integer numCaseNotes;

    @Schema(required = true, description = "Last Date of this case note type/subtype")
    @NotNull
    private LocalDate latestCaseNote;

}
