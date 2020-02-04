package uk.gov.justice.digital.hmpps.keyworker.rolemigration;

import com.microsoft.applicationinsights.TelemetryClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.client.HttpClientErrorException;
import uk.gov.justice.digital.hmpps.keyworker.dto.RoleAssignmentStats;
import uk.gov.justice.digital.hmpps.keyworker.dto.RoleAssignmentsSpecification;

import javax.validation.Valid;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toList;

@Service
@Slf4j
@Validated
public class RoleAssignmentsService {

    private final RoleService roleService;
    private final TelemetryClient telemetryClient;

    public RoleAssignmentsService(final RoleService roleService, final TelemetryClient telemetryClient) {
        this.roleService = roleService;
        this.telemetryClient = telemetryClient;
    }

    /**
     * Look up users by caseload (prison) and membership of specification.rolesToMatch.
     * for each matched user:
     * Assign to the user the roles identified in specification.rolesToAssign.
     * Remove from the user (at the caseload) the roles identified by specification.rolesToRemove.
     */
    @PreAuthorize("hasAnyRole('MAINTAIN_ACCESS_ROLES_ADMIN')")
    public List<RoleAssignmentStats> updateRoleAssignments(@Valid final RoleAssignmentsSpecification specification) {
        log.info("Updating role assignments: {}", specification);
        return specification.getCaseloads().stream().map(caseload -> performAssignment(specification, caseload)).collect(toList());
    }

    private RoleAssignmentStats performAssignment(final RoleAssignmentsSpecification specification, final String caseload) {

        final var usernamesForCaseload = findUsernamesMatchingRolesAtCaseload(specification.getRolesToMatch(), caseload);

        log.info("Found {} users for the {} caseload: {}.", usernamesForCaseload.size(), caseload, usernamesForCaseload);
        final var results = RoleAssignmentStats.builder()
                .numMatchedUsers(usernamesForCaseload.size())
                .caseload(caseload)
                .build();

        usernamesForCaseload.forEach(username -> {
            final var assignmentSuccess = assignRolesToUser(results, username, specification.getRolesToAssign());
            if (assignmentSuccess) {
                removeRolesFromUserAtCaseload(results, caseload, username, specification.getRolesToRemove());
            }
        });

        telemetryClient.trackEvent("UpdateRoleAssignment", results.toMap(), null);
        return results;
    }

    private boolean assignRolesToUser(final RoleAssignmentStats stats, final String username, final List<String> rolesToAssign) {
        return rolesToAssign.stream().reduce(true, (allSuccess, role) -> {
            if (allSuccess) {
                final var success = assignRole(stats, username, role);
                return success;
            }
            stats.incrementAssignmentFailure();
            return false;
        }, (map1, map2) -> map1);
    }

    private boolean assignRole(final RoleAssignmentStats stats, final String username, final String roleCodeToAssign) {
        try {
            roleService.assignRoleToApiCaseload(username, roleCodeToAssign);
            stats.incrementAssignmentSuccess();
            return true;
        } catch (final Exception e) {
            final var message = String.format("Failure while assigning roles %1$s to user %2$s. No roles will be removed from this user. Continuing with next user.", roleCodeToAssign, username);
            log.warn(message, e);
            stats.incrementAssignmentFailure();
            return false;
        }
    }

    private void removeRolesFromUserAtCaseload(final RoleAssignmentStats stats, final String caseload, final String username, final List<String> rolesToRemove) {
        rolesToRemove.stream().reduce(true, (allSuccess, role) -> {
            if (allSuccess) {
                final var success = removeRole(stats, caseload, username, role);
                return success;
            }
            stats.incrementUnassignmentFailure();
            return false;
        }, (map1, map2) -> map1);
    }

    private boolean removeRole(final RoleAssignmentStats stats, final String caseload, final String username, final String roleCodeToRemove) {
        try {
            roleService.removeRole(username, caseload, roleCodeToRemove);
            stats.incrementUnassignmentSuccess();
            return true;
        } catch (final HttpClientErrorException.NotFound notFoundException) {
            log.info("Username {} does not have role {} at caseload {}", username, roleCodeToRemove, caseload);
            stats.incrementUnassignmentIgnore();
            return true;
        } catch (final Exception e) {
            final var message = String.format("Failure while removing roles %1$s from user %2$s at caseload %3$s. Some roles may not have been removed. Continuing with next user.", roleCodeToRemove, username, caseload);
            log.warn(message, e);
            stats.incrementUnassignmentFailure();
            return false;
        }
    }

    private Set<String> findUsernamesMatchingRolesAtCaseload(final List<String> rolesToMatch,
                                                             final String caseload) {
        return rolesToMatch
                .stream()
                .flatMap(roleCodeToMatch -> roleService.findUsersForPrisonHavingRole(caseload, roleCodeToMatch).stream())
                .collect(toCollection(TreeSet::new));
    }
}
