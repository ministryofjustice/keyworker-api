package uk.gov.justice.digital.hmpps.keyworker.rolemigration;

import java.util.Set;

public interface RoleService {

    /**
     * @param prisonId The identifier of a prison (also known as 'caseload')
     * @param roleCode The roleCode of the role
     * @return The usernames of the users who have been assigned the role at the prison
     */
    Set<String> findUsersForPrisonHavingRole(String prisonId, String roleCode);

    void removeRole(String username, String prisonId, String roleCode);

    void assignRoleToApiCaseload(String username, String roleCode);
}
