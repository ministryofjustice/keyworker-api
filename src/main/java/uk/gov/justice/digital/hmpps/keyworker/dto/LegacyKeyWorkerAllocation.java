package uk.gov.justice.digital.hmpps.keyworker.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.format.annotation.DateTimeFormat;
import uk.gov.justice.digital.hmpps.keyworker.model.AllocationType;

@ApiModel(description = "Offender allocation history details")

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@ToString(exclude = {"firstName", "lastName"})
public class LegacyKeyWorkerAllocation {

    @Schema(required = true, description = "Id of offender allocation.")
    @NotNull
    private Long offenderKeyworkerId;

    @Schema(required = true, description = "The offender's Key worker staff Id.")
    @NotNull
    private Long staffId;

    @Schema(required = true, description = "Key worker's first name.")
    @NotBlank
    private String firstName;

    @Schema(required = true, description = "Key worker's last name.")
    @NotBlank
    private String lastName;

    @Schema(required = true, description = "Prison Id where allocation is effective.")
    @NotBlank
    private String prisonId;

    @Schema(required = true, description = "The date and time of the allocation.")
    @NotNull
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime assigned;

    @Schema(required = false, description = "The date and time of deallocation.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime expired;

    @Schema(required = true, description = "The user who created the allocation.")
    @NotBlank
    private StaffUser userId;

    @Schema(required = true, description = "Whether allocation is active.")
    @NotBlank
    private boolean active;

    @Schema(required = true, description = "Type of allocation - auto or manual.")
    @NotNull
    private AllocationType allocationType;

    @Schema(required = true, description = "Reason for allocation.")
    @NotNull
    private String allocationReason;

    @Schema(description = "Reason for de-allocation.")
    private String deallocationReason;

    @Schema(required = false, description = "The date and time of creation.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime creationDateTime;

    @Schema(required = true, description = "The user who created the allocation.")
    @NotBlank
    private StaffUser createdByUser;

    @Schema(required = false, description = "Last date and time of modification.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime modifyDateTime;

    @Schema(required = true, description = "The user who last modified the allocation.")
    private StaffUser lastModifiedByUser;

}
