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
import static uk.gov.justice.digital.hmpps.keyworker.rolemigration.RoleAssignmentsService.Status.*;

@Service
@Slf4j
@Validated
public class RoleAssignmentsService {

    public enum Status {SUCCESS, FAIL, IGNORE}

    private final RoleService roleService;
    private final TelemetryClient telemetryClient;

    public RoleAssignmentsService(RoleService roleService, TelemetryClient telemetryClient) {
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
    public Map<String, RoleAssignmentStats> updateRoleAssignments(@Valid final RoleAssignmentsSpecification specification) {
        log.info("Updating role assignments: {}", specification);
        val caseloadResults = new TreeMap<String, RoleAssignmentStats>();
        for (val caseload : specification.getCaseloads()) {

            val usernamesForCaseload = findUsernamesMatchingRolesAtCaseload(specification.getRolesToMatch(), caseload);
            log.info("Found {} users for the {} caseload: {}.", usernamesForCaseload.size(), caseload, usernamesForCaseload);
            val results = new RoleAssignmentStats();

            for (val username : usernamesForCaseload) {
                var success = assignRolesToUser(results, username, specification.getRolesToAssign());
                if (success) {
                    removeRolesFromUserAtCaseload(results, caseload, username, specification.getRolesToRemove());
                }
            }
            var infoMap = Map.of(
                    "prisonId", caseload,
                    "numUsersMatched", String.valueOf(usernamesForCaseload.size()),
                    "numAssignRoleSucceeded", String.valueOf(results.getNumAssignRoleSucceeded()),
                    "numAssignRoleFailed", String.valueOf(results.getNumAssignRoleFailed()),
                    "numUnAssignRoleSucceeded", String.valueOf(results.getNumUnAssignRoleSucceeded()),
                    "numUnAssignRoleIgnored", String.valueOf(results.getNumUnAssignRoleIgnored()),
                    "numUnAssignRoleFailed", String.valueOf(results.getNumUnAssignRoleFailed()));
            telemetryClient.trackEvent("UpdateRollAssignment", infoMap,null);
            caseloadResults.put(caseload,results);
        }
        return caseloadResults;
    }

    private boolean assignRolesToUser(final RoleAssignmentStats results, final String username, final List<String> rolesToAssign) {
        boolean hasFail = false;
        for (val roleCodeToAssign : rolesToAssign) {
            if (!hasFail) {
                val status = assignRole(username, roleCodeToAssign);
                results.addAssignResult(status);
                if(status == FAIL){
                    hasFail = true;
                }
            } else {
                results.addAssignResult(FAIL);
            }
        }
        return !hasFail;
    }

    private Status assignRole(final String username, final String roleCodeToAssign) {
        try {
            roleService.assignRoleToApiCaseload(username, roleCodeToAssign);
            return SUCCESS;

        } catch (Exception e) {
            val message = String.format("Failure while assigning roles %1$s to user %2$s. No roles will be removed from this user. Continuing with next user.", roleCodeToAssign, username);
            log.warn(message, e);
            return FAIL;
        }
    }

    private void removeRolesFromUserAtCaseload(final RoleAssignmentStats results, final String caseload, final String username, final List<String> rolesToRemove) {
        boolean hasFail = false;
        for (val roleCodeToRemove : rolesToRemove) {
            if (!hasFail) {
                val status = removeRole(caseload, username, roleCodeToRemove);
                results.addUnAssignResult(status);
                if(status == FAIL){
                    hasFail = true;
                }
            } else {
                results.addUnAssignResult(FAIL);
            }
        }
    }

    private Status removeRole(final String caseload, final String username, final String roleCodeToRemove) {
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

    private Set<String> findUsernamesMatchingRolesAtCaseload(final List<String> rolesToMatch, final String caseload) {
        return rolesToMatch
                .stream()
                .flatMap(roleCodeToMatch -> roleService.findUsersForPrisonHavingRole(caseload, roleCodeToMatch).stream())
                .collect(toCollection(TreeSet::new));
    }
}
