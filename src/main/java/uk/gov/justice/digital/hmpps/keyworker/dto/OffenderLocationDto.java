package uk.gov.justice.digital.hmpps.keyworker.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.NotBlank;

import javax.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@ApiModel(description = "Offender Summary")

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OffenderLocationDto {

        private String offenderNo;

        private Long bookingId;

        private String firstName;

        private String middleName;

        private String lastName;

        private LocalDate dateOfBirth;

        private String agencyId;

        private Long assignedLivingUnitId;

        private String assignedLivingUnitDesc;

        private Long facialImageId;

        private String assignedOfficerUserId;

        private List<String> aliases;

        private String iepLevel;
}
