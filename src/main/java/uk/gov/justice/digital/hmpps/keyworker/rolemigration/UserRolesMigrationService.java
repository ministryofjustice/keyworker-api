package uk.gov.justice.digital.hmpps.keyworker.rolemigration;


import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import uk.gov.justice.digital.hmpps.keyworker.dto.RoleAssignmentsSpecification;

import javax.validation.Valid;
import java.util.*;
import java.util.function.BinaryOperator;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@Validated
public class UserRolesMigrationService {

    private static final BinaryOperator<Set<String>> SET_UNION = (a, b) -> {
        Set<String> result = new HashSet<>(a);
        result.addAll(b);
        return result;
    };

    private final RoleService roleService;
    private final Set<String> rolesToMatch;
    private final Set<String> rolesToAssign;
    private final Set<String> rolesToMigrate;

    public UserRolesMigrationService(final RoleService roleService, final RoleMigrationConfiguration configuration) {
        this.roleService = roleService;
        rolesToMatch = Collections.unmodifiableSet(new HashSet<>(configuration.getRolesToMatch()));
        rolesToAssign = Collections.unmodifiableSet(new HashSet<>(configuration.getRolesToAssign()));
        rolesToMigrate = Collections.unmodifiableSet(new HashSet<>(configuration.getRolesToMigrate()));
    }

    public void migrate(final String prisonId) {
        migrateRoles(prisonId, rolesToMatch, rolesToAssign, rolesToMigrate);
    }

    /**
     * Look up users by caseload (prison) and membership of specification.rolesToMatch.
     * for each matched user:
     * Assign to the user the roles identified in specification.rolesToAssign.
     * Remove from the user (at the caseload) the roles identified by specification.rolesToRemove.
     */
    @PreAuthorize("hasAnyRole('MAINTAIN_ACCESS_ROLES', 'MAINTAIN_ACESS_ROLES_ADMIN')")
    public void updateRoleAssignments(@Valid final RoleAssignmentsSpecification specification) {
        log.debug("Updating role assignments: {}", specification);
        for (val caseload : specification.getCaseloads()) {

            val usernamesForCaseload = findUsernamesMatchingRolesAtCaseload(specification.getRolesToMatch(), caseload);
            log.debug("Found {} users for the {} caseload: {}.", usernamesForCaseload.size(), caseload, usernamesForCaseload);
            usernamesForCaseload.forEach(username -> {
                try {
                    assignRolesToUser(username, specification.getRolesToAssign());
                    try {
                        removeRolesFromUserAtCaseload(caseload, username, specification.getRolesToRemove());
                    } catch (Exception e) {
                        log.debug("Failure while removing roles {} from user {} at caseload {}. Some roles may not have been removed.", specification.getRolesToRemove(), username, caseload);
                    }
                } catch (Exception e) {
                    log.debug("Failure while assigning roles {} to user {}. No roles will be removed from this user.", specification.getRolesToAssign(), username);
                }
            });
        }
    }

    private void removeRolesFromUserAtCaseload(String caseload, String username, List<String> rolesToRemove) {
        rolesToRemove.forEach(roleCodeToRemove -> {
            try {
                roleService.removeRole(username, caseload, roleCodeToRemove);

            } catch (HttpClientErrorException.NotFound notFoundException) {
                log.debug("Username {} does not have role {} at caseload {}", username, roleCodeToRemove, caseload);
            }
        });
    }

    private void assignRolesToUser(String username, List<String> rolesToAssign) {
        rolesToAssign.forEach(roleCodeToAssign -> roleService.assignRoleToApiCaseload(username, roleCodeToAssign));
    }

    private Set<String> findUsernamesMatchingRolesAtCaseload(List<String> rolesToMatch, String caseload) {
        return rolesToMatch
                .stream()
                .flatMap(roleCodeToMatch -> roleService.findUsersForPrisonHavingRole(caseload, roleCodeToMatch).stream())
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private void migrateRoles(final String prisonId, final Set<String> rolesToMatch,
                              final Set<String> rolesToAssign, final Set<String> rolesToMigrate) {

        final var usernamesByRole = findUsernamesByRole(prisonId, rolesToMatch);

        assignRolesToUsers(rolesToAssign, usernamesForUsersHavingRoles(usernamesByRole, rolesToMigrate));

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
    private List<Pair<String, Set<String>>> findUsernamesByRole(final String prisonId, final Set<String> roleCodes) {
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
     * @param filterRoles     roles to include
     * @return all the supplied usernames as a set.
     */
    private Set<String> usernamesForUsersHavingRoles(final List<Pair<String, Set<String>>> usernamesByRole,
                                                     final Set<String> filterRoles) {
        return usernamesByRole.stream().filter(p -> filterRoles.contains(p.getLeft())).map(Pair::getRight).reduce(new HashSet<>(), SET_UNION);
    }

    /**
     * Assign the supplied roles to all the users represented by the supplied set of usernames. This is an assignment
     * to the 'API' caseload.
     *
     * @param rolesToAssign The set of role codes to assign to the users.
     * @param usernames     The set of usernames to which the assignments should be made.
     */
    private void assignRolesToUsers(final Set<String> rolesToAssign, final Set<String> usernames) {
        usernames.forEach(username ->
                rolesToAssign.forEach(roleToAssign -> {
                    try {
                        roleService.assignRoleToApiCaseload(username, roleToAssign);
                    } catch (final HttpServerErrorException e) {
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
    private void removeRolesFromStaff(final String prisonId, final List<Pair<String, Set<String>>> usernamesByRole) {
        usernamesByRole.forEach(roleAndUsernames ->
        {
            final var roleCode = roleAndUsernames.getLeft();
            roleAndUsernames.getRight().forEach(username -> {
                        try {
                            roleService.removeRole(username, prisonId, roleCode);
                        } catch (final HttpServerErrorException e) {
                            log.info("Role removal (username {}, prisonId {}, role {}) failed", username, prisonId, roleCode);
                        }
                    }
            );
        });
    }

}
