package uk.gov.justice.digital.hmpps.keyworker.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.NotBlank;
import org.springframework.format.annotation.DateTimeFormat;

import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OffenderKeyworkerDto {

    @ApiModelProperty(required = true, value = "Id of offender allocation.")
    @NotNull
    private Long offenderKeyworkerId;

    @ApiModelProperty(required = true, value = "The offender's unique offender number (aka NOMS Number in the UK).")
    @NotBlank
    private String offenderNo;

    @ApiModelProperty(required = true, value = "The offender's Key worker.")
    @NotNull
    private Long staffId;

    @ApiModelProperty(required = true, value = "Prison Id where allocation is effective.")
    @NotBlank
    private String agencyId;

    @ApiModelProperty(required = true, value = "The date and time of the allocation.")
    @NotNull
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime assigned;

    @ApiModelProperty(required = false, value = "The date and time of deallocation.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime expired;

    @ApiModelProperty(required = true, value = "The user who created the allocation.")
    @NotBlank
    private String userId;

    @ApiModelProperty(required = true, value = "Whether allocation is active.")
    @NotBlank
    private String active;
}
