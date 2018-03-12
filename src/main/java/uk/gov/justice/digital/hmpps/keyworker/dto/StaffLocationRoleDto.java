package uk.gov.justice.digital.hmpps.keyworker.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StaffLocationRoleDto {
    private Long staffId;
    private String firstName;
    private String lastName;
    private String email;
    private Long thumbnailId;
    private String agencyId;
    private LocalDate fromDate;
    private LocalDate toDate;
    private String position;
    private String positionDescription;
    private String role;
    private String roleDescription;
    private String scheduleType;
    private String scheduleTypeDescription;
    private BigDecimal hoursPerWeek;
}
