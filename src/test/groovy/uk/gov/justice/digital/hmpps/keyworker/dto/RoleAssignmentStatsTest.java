package uk.gov.justice.digital.hmpps.keyworker.dto;

import lombok.val;
import org.junit.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class RoleAssignmentStatsTest {

    @Test
    public void checkToMap() {

        val expected = RoleAssignmentStats.builder()
                .caseload("MDI")
                .numMatchedUsers(6)
                .numAssignRoleSucceeded(4)
                .numAssignRoleFailed(2)
                .numUnassignRoleSucceeded(1)
                .numUnassignRoleIgnored(3)
                .numUnassignRoleFailed(5)
                .build();

        assertThat(expected.toMap()).isEqualTo(Map.of(
                "caseload", "MDI",
                "numUsersMatched", "6",
                "numAssignRoleSucceeded", "4",
                "numAssignRoleFailed", "2",
                "numUnassignRoleSucceeded", "1",
                "numUnassignRoleIgnored", "3",
                "numUnassignRoleFailed", "5"));
    }

}