package uk.gov.justice.digital.hmpps.keyworker.rolemigration;


import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpServerErrorException;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BinaryOperator;
import java.util.stream.Collectors;

@Slf4j
@Service
public class UserRolesMigrationService {

    private static final BinaryOperator<Set<String>> SET_UNION = (a, b) -> {
        Set<String> result = new HashSet<>(a);
        result.addAll(b);
        return result;
    };

    private final RoleService roleService;
    private final Set<String> rolesToMatch;
    private final Set<String> rolesToAssign;

    public UserRolesMigrationService(RoleService roleService, RoleMigrationConfiguration configuration) {
        this.roleService = roleService;
        rolesToMatch = Collections.unmodifiableSet(new HashSet<>(configuration.getRolesToMatch()));
        rolesToAssign = Collections.unmodifiableSet(new HashSet<>(configuration.getRolesToAssign()));
    }

    public void migrate(String prisonId) {
        migrateRoles(prisonId, rolesToMatch, rolesToAssign);
    }

    private void migrateRoles(String prisonId, Set<String> rolesToMatch, Set<String> rolesToAssign) {

        List<Pair<String, Set<String>>> usernamesByRole = findUsernamesByRole(prisonId, rolesToMatch);

        assignRolesToUsers(rolesToAssign, allUsernames(usernamesByRole));

        removeRolesFromStaff(prisonId, usernamesByRole);
    }

    /**
     * Use RoleService#findUsernamesByRole to retrieve Sets of username for each roleCode. Return the
     * results as Pairs of (roleCode, set of username) Searches are restricted to the set of users serving a prison, also
     * known as a caseload.
     *
     * @param prisonId  The prison/caseload identifier for which usernames are required
     * @param roleCodes The set of role codes to use to group users
     * @return a list of pairs of (roleCode, set of username)
     */
    private List<Pair<String, Set<String>>> findUsernamesByRole(String prisonId, Set<String> roleCodes) {
        return roleCodes
                .stream()
                .map(sourceRole -> Pair.of(
                        sourceRole,
                        roleService.findUsersForPrisonHavingRole(prisonId, sourceRole)))
                .collect(Collectors.toList());
    }

    /**
     * Extract the set of all usernames contained in the supplied data structure.
     *
     * @param usernamesByRole usernames grouped by role membership.
     * @return all the supplied usernames as a set.
     */
    private Set<String> allUsernames(List<Pair<String, Set<String>>> usernamesByRole) {
        return usernamesByRole.stream().map(Pair::getRight).reduce(new HashSet<>(), SET_UNION);
    }

    /**
     * Assign the supplied roles to all the users represented by the supplied set of usernames. This is an assignment
     * to the 'API' caseload.
     *
     * @param rolesToAssign The set of role codes to assign to the users.
     * @param usernames     The set of usernames to which the assignments should be made.
     */
    private void assignRolesToUsers(Set<String> rolesToAssign, Set<String> usernames) {
        usernames.forEach(username ->
                rolesToAssign.forEach(roleToAssign -> {
                    try {
                        roleService.assignRoleToApiCaseload(username, roleToAssign);
                    } catch (HttpServerErrorException e) {
                        log.info("API caseload role assignment (username {}, role {}) failed. (Does the role assignment already exist?).", username, roleToAssign);
                    }
                }));
    }

    /**
     * Remove the all the role associations (prisonId, roleCode, username) represented by the supplied
     * data structure.
     *
     * @param prisonId        The prison / caseload
     * @param usernamesByRole sets of usernames by role code.
     */
    private void removeRolesFromStaff(String prisonId, List<Pair<String, Set<String>>> usernamesByRole) {
        usernamesByRole.forEach(roleAndUsernames ->
        {
            final String roleCode = roleAndUsernames.getLeft();
            roleAndUsernames.getRight().forEach(username -> {
                        try {
                            roleService.removeRole(username, prisonId, roleCode);
                        } catch (HttpServerErrorException e) {
                            log.info("Role removal (username {}, prisonId {}, role {}) failed", username, prisonId, roleCode);
                        }
                    }
            );
        });
    }
}
