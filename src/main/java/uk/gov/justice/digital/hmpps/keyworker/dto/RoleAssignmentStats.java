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
    @ApiModelProperty(required = true, value = "Number of role un-assignments succeeded")
    private long numUnAssignRoleSucceeded;
    @ApiModelProperty(required = true, value = "Number of role run-assignments ignored - When role does not exist")
    private long numUnAssignRoleIgnored;
    @ApiModelProperty(required = true, value = "Number of role un-assignments failed")
    private long numUnAssignRoleFailed;


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

    public void addUnAssignResult(final RoleAssignmentsService.Status status) {
        switch (status) {
            case SUCCESS:
                numUnAssignRoleSucceeded++;
                break;
            case FAIL:
                numUnAssignRoleFailed++;
                break;
            case IGNORE:
                numUnAssignRoleIgnored++;
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + status);
        }
    }
}