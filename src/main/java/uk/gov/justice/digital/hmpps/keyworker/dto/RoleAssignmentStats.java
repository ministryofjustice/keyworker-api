package uk.gov.justice.digital.hmpps.keyworker.dto;

import io.swagger.annotations.ApiModelProperty;
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

    @ApiModelProperty(required = true, value = "Caseload")
    private String caseload;
    @ApiModelProperty(required = true, value = "Number of matched users")
    private int numMatchedUsers;
    @ApiModelProperty(required = true, value = "Number of role assignments succeeded")
    private long numAssignRoleSucceeded;
    @ApiModelProperty(required = true, value = "Number of role assignments failed")
    private long numAssignRoleFailed;
    @ApiModelProperty(required = true, value = "Number of role unassignments succeeded")
    private long numUnassignRoleSucceeded;
    @ApiModelProperty(required = true, value = "Number of role unassignments ignored - When role does not exist")
    private long numUnassignRoleIgnored;
    @ApiModelProperty(required = true, value = "Number of role unassignments failed")
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