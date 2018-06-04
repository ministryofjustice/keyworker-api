package uk.gov.justice.digital.hmpps.keyworker.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@EqualsAndHashCode(of = { "staffId" })
@ToString(exclude={"firstName","lastName","email"})
public class StaffLocationRoleDto {
    private Long staffId;
    private String firstName;
    private String lastName;
    private String email;
    private Long thumbnailId;
    private String agencyId;
    private String agencyDescription;
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
