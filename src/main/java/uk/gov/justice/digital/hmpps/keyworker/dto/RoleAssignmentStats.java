package uk.gov.justice.digital.hmpps.keyworker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.Map;

@Builder
@Getter
@EqualsAndHashCode
@ToString
public class RoleAssignmentStats {

    @Schema(required = true, description = "Caseload")
    private String caseload;
    @Schema(required = true, description = "Number of matched users")
    private int numMatchedUsers;
    @Schema(required = true, description = "Number of role assignments succeeded")
    private long numAssignRoleSucceeded;
    @Schema(required = true, description = "Number of role assignments failed")
    private long numAssignRoleFailed;
    @Schema(required = true, description = "Number of role unassignments succeeded")
    private long numUnassignRoleSucceeded;
    @Schema(required = true, description = "Number of role unassignments ignored - When role does not exist")
    private long numUnassignRoleIgnored;
    @Schema(required = true, description = "Number of role unassignments failed")
    private long numUnassignRoleFailed;

    public void incrementAssignmentFailure() {
        numAssignRoleFailed++;
    }

    public void incrementAssignmentSuccess() {
        numAssignRoleSucceeded++;
    }

    public void incrementUnassignmentSuccess() {
        numUnassignRoleSucceeded++;
    }

    public void incrementUnassignmentFailure() {
        numUnassignRoleFailed++;
    }


    public void incrementUnassignmentIgnore() {
        numUnassignRoleIgnored++;
    }

    public Map<String, String> toMap() {
        return Map.of(
            "caseload", caseload,
            "numUsersMatched", String.valueOf(numMatchedUsers),
            "numAssignRoleSucceeded", String.valueOf(getNumAssignRoleSucceeded()),
            "numAssignRoleFailed", String.valueOf(getNumAssignRoleFailed()),
            "numUnassignRoleSucceeded", String.valueOf(getNumUnassignRoleSucceeded()),
            "numUnassignRoleIgnored", String.valueOf(getNumUnassignRoleIgnored()),
            "numUnassignRoleFailed", String.valueOf(getNumUnassignRoleFailed()));
    }
}