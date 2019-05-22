package uk.gov.justice.digital.hmpps.keyworker.rolemigration;

import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.client.HttpClientErrorException;
import uk.gov.justice.digital.hmpps.keyworker.dto.RoleAssignmentsSpecification;

import javax.validation.Valid;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

@Service
@Slf4j
@Validated
public class RoleAssignmentsService {

    private final RoleService roleService;

    public RoleAssignmentsService(RoleService roleService) {
        this.roleService = roleService;
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
}
