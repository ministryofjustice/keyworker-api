package uk.gov.justice.digital.hmpps.keyworker.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import javax.validation.constraints.NotNull;
import java.time.LocalDate;

@ApiModel(description = "Case Note Usage")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@EqualsAndHashCode(of = {"staffId","caseNoteType","caseNoteSubType"})
@ToString
public class CaseNoteUsageDto {

    @ApiModelProperty(required = true, value = "Unique staff identifier for Key worker.")
    @NotNull
    private Long staffId;

    @ApiModelProperty(required = true, value = "Case Note Type")
    @NotNull
    private String caseNoteType;

    @ApiModelProperty(required = true, value = "Case Note Sub Type")
    @NotNull
    private String caseNoteSubType;

    @ApiModelProperty(required = true, value = "Number of case notes of this type")
    @NotNull
    private Integer numCaseNotes;

    @ApiModelProperty(required = true, value = "Last Date of this case note type/subtype")
    @NotNull
    private LocalDate latestCaseNote;

}
