package uk.gov.justice.digital.hmpps.keyworker.rolemigration.remote;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)

class StaffUserRoleDto {
    private Long roleId;
    private String roleCode;
    private String roleName;
    private String parentRoleCode;
    private String caseloadId;
    private String username;
    private Long staffId;
}
