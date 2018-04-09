package uk.gov.justice.digital.hmpps.keyworker.rolemigration;

import java.util.Set;

public interface RoleService {

    Set<Long> findStaffForPrisonHavingRole(String prisonId, String roleCode);

    void removeRole(long staffId, String prisonId, String roleCode);

    void assignRoleToApiCaseload(long staffId, String roleCode);
}
