package uk.gov.justice.digital.hmpps.keyworker.dto;

import io.swagger.annotations.ApiModelProperty;
import lombok.*;
import uk.gov.justice.digital.hmpps.keyworker.rolemigration.RoleAssignmentsService;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@ToString
@EqualsAndHashCode
public class RoleAssignmentStats {

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


    public void addAssignResult(final RoleAssignmentsService.Status status) {
        switch (status) {
            case SUCCESS:
                numAssignRoleSucceeded++;
                break;
            case FAIL:
                numAssignRoleFailed++;
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + status);
        }
    }

    public void addUnassignResult(final RoleAssignmentsService.Status status) {
        switch (status) {
            case SUCCESS:
                numUnassignRoleSucceeded++;
                break;
            case FAIL:
                numUnassignRoleFailed++;
                break;
            case IGNORE:
                numUnassignRoleIgnored++;
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + status);
        }
    }
}