package uk.gov.justice.digital.hmpps.keyworker.rolemigration;

import com.microsoft.applicationinsights.TelemetryClient;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.client.HttpClientErrorException;
import uk.gov.justice.digital.hmpps.keyworker.dto.RoleAssignmentStats;
import uk.gov.justice.digital.hmpps.keyworker.dto.RoleAssignmentsSpecification;

import javax.validation.Valid;
import java.util.*;

import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toList;
import static uk.gov.justice.digital.hmpps.keyworker.dto.RoleAssignmentStats.Status.*;

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
        return specification.getCaseloads().stream().map(caseload -> getResultsForCaseload(specification, caseload)).collect(toList());
    }

    private RoleAssignmentStats getResultsForCaseload(@Valid RoleAssignmentsSpecification specification, String caseload) {

        val usernamesForCaseload = findUsernamesMatchingRolesAtCaseload(specification.getRolesToMatch(), caseload);

        log.info("Found {} users for the {} caseload: {}.", usernamesForCaseload.size(), caseload, usernamesForCaseload);
        val results = RoleAssignmentStats.builder()
                .numMatchedUsers(usernamesForCaseload.size())
                .caseload(caseload)
                .build();

        usernamesForCaseload.forEach(username -> {
            val assignResults = assignRolesToUser(username, specification.getRolesToAssign());
            val unassigningResults = assignResults.containsKey(FAIL)
                    ? Map.<RoleAssignmentStats.Status, Long>of()
                    : removeRolesFromUserAtCaseload(caseload, username, specification.getRolesToRemove());
            results.addAssignResults(assignResults);
            results.addUnassignResults(unassigningResults);
        });

        telemetryClient.trackEvent("UpdateRollAssignment", results.toMap(), null);
        return results;
    }

    private Map<RoleAssignmentStats.Status, Long> assignRolesToUser(final String username, final List<String> rolesToAssign) {
        return rolesToAssign.stream().reduce(new HashMap<RoleAssignmentStats.Status, Long>(), (results, role) -> {
            if (!results.containsKey(FAIL)) {
                val status = assignRole(username, role);
                results.compute(status, (key, count) -> count == null ? 1 : count + 1);
                return results;
            }
            results.put(FAIL, results.get(FAIL) + 1);
            return results;
        }, (map1, map2) -> map1);
    }

    private RoleAssignmentStats.Status assignRole(final String username, final String roleCodeToAssign) {
        try {
            roleService.assignRoleToApiCaseload(username, roleCodeToAssign);
            return SUCCESS;

        } catch (Exception e) {
            val message = String.format("Failure while assigning roles %1$s to user %2$s. No roles will be removed from this user. Continuing with next user.", roleCodeToAssign, username);
            log.warn(message, e);
            return FAIL;
        }
    }

    private Map<RoleAssignmentStats.Status, Long> removeRolesFromUserAtCaseload(final String caseload, final String username,
                                                                                final List<String> rolesToRemove) {
        return rolesToRemove.stream().reduce(new HashMap<RoleAssignmentStats.Status, Long>(), (results, role) -> {
            if (!results.containsKey(FAIL)) {
                val status = removeRole(caseload, username, role);
                results.compute(status, (key, count) -> count == null ? 1 : count + 1);
                return results;
            }
            results.put(FAIL, results.get(FAIL) + 1);
            return results;
        }, (map1, map2) -> map1);
    }

    private RoleAssignmentStats.Status removeRole(final String caseload, final String username, final String roleCodeToRemove) {
        try {
            roleService.removeRole(username, caseload, roleCodeToRemove);
            return SUCCESS;
        } catch (HttpClientErrorException.NotFound notFoundException) {
            log.info("Username {} does not have role {} at caseload {}", username, roleCodeToRemove, caseload);
            return IGNORE;
        } catch (Exception e) {
            val message = String.format("Failure while removing roles %1$s from user %2$s at caseload %3$s. Some roles may not have been removed. Continuing with next user.", roleCodeToRemove, username, caseload);
            log.warn(message, e);
            return FAIL;
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
