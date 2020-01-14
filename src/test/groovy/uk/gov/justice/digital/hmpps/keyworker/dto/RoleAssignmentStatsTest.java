package uk.gov.justice.digital.hmpps.keyworker.dto;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RoleAssignmentStatsTest {

    @Test
    void checkToMap() {

        final var expected = RoleAssignmentStats.builder()
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
