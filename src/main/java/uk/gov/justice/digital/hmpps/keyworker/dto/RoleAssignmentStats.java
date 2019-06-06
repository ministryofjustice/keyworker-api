package uk.gov.justice.digital.hmpps.keyworker.dto;

import io.swagger.annotations.ApiModelProperty;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.Map;

@Builder
@Getter
@EqualsAndHashCode
public class RoleAssignmentStats {

    public enum Status {SUCCESS, FAIL, IGNORE}

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

    public void addAssignResults(Map<Status, Long> assignResults) {
        assignResults.forEach((status, count) -> {
            switch (status) {
                case SUCCESS:
                    numAssignRoleSucceeded+= count;
                    break;
                case FAIL:
                    numAssignRoleFailed+= count;
                    break;
                default:
                    throw new IllegalStateException("Unexpected value: " + status);
            }
        });

    }

    public void addUnassignResults(Map<Status, Long> unassigningResults) {
        unassigningResults.forEach(((status, count) -> {
            switch (status) {
                case SUCCESS:
                    numUnassignRoleSucceeded+= count;
                    break;
                case FAIL:
                    numUnassignRoleFailed+= count;
                    break;
                case IGNORE:
                    numUnassignRoleIgnored+= count;
                    break;
                default:
                    throw new IllegalStateException("Unexpected value: " + status);
            }
        }));
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