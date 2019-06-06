package uk.gov.justice.digital.hmpps.keyworker.rolemigration;

import com.microsoft.applicationinsights.TelemetryClient;
import lombok.val;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.web.client.HttpClientErrorException;
import uk.gov.justice.digital.hmpps.keyworker.dto.RoleAssignmentStats;
import uk.gov.justice.digital.hmpps.keyworker.dto.RoleAssignmentsSpecification;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class RoleAssignmentsServiceTest {

    @Mock
    private RoleService roleService;
    @Mock
    private TelemetryClient telemetryClient;

    @InjectMocks
    private RoleAssignmentsService service;

    @Test
    public void givenCaseloadAndRoleToMatch() {
        when(roleService.findUsersForPrisonHavingRole(any(), any())).thenReturn(Set.of());

        service.updateRoleAssignments(RoleAssignmentsSpecification.builder()
                .caseloads(List.of("MDI"))
                .rolesToMatch(List.of("R"))
                .build());

        verify(roleService).findUsersForPrisonHavingRole("MDI", "R");
        verifyNoMoreInteractions(roleService);
    }

    @Test
    public void givenMultipleCaseloadAndRoleToMatch() {
        when(roleService.findUsersForPrisonHavingRole(any(), any())).thenReturn(Set.of());

        service.updateRoleAssignments(RoleAssignmentsSpecification.builder()
                .caseloads(List.of("MDI", "LEI"))
                .rolesToMatch(List.of("R", "Q"))
                .build());

        verify(roleService).findUsersForPrisonHavingRole("MDI", "R");
        verify(roleService).findUsersForPrisonHavingRole("MDI", "Q");
        verify(roleService).findUsersForPrisonHavingRole("LEI", "R");
        verify(roleService).findUsersForPrisonHavingRole("LEI", "Q");
        verifyNoMoreInteractions(roleService);
    }

    @Test
    public void addRoles() {
        when(roleService.findUsersForPrisonHavingRole("MDI", "R")).thenReturn(Set.of("U1"));

        service.updateRoleAssignments(RoleAssignmentsSpecification.builder()
                .caseloads(List.of("MDI"))
                .rolesToMatch(List.of("R"))
                .rolesToAssign(List.of("X"))
                .build());

        verify(roleService).assignRoleToApiCaseload("U1", "X");
    }


    @Test
    public void addRolesMultipleCaseloads() {
        when(roleService.findUsersForPrisonHavingRole("MDI", "R")).thenReturn(Set.of("U1", "U2"));
        when(roleService.findUsersForPrisonHavingRole("LEI", "R")).thenReturn(Set.of("U2", "U3"));

        service.updateRoleAssignments(RoleAssignmentsSpecification.builder()
                .caseloads(List.of("MDI", "LEI"))
                .rolesToMatch(List.of("R"))
                .rolesToAssign(List.of("X"))
                .build());

        verify(roleService).assignRoleToApiCaseload("U1", "X");
        verify(roleService, atLeastOnce()).assignRoleToApiCaseload("U2", "X");
        verify(roleService).assignRoleToApiCaseload("U3", "X");

        verify(roleService, atLeastOnce()).findUsersForPrisonHavingRole(any(), any());
        verifyNoMoreInteractions(roleService);
    }

    @Test
    public void addRolesMultipleRolesToAdd() {
        when(roleService.findUsersForPrisonHavingRole("MDI", "R")).thenReturn(Set.of("U1", "U2"));

        service.updateRoleAssignments(RoleAssignmentsSpecification.builder()
                .caseloads(List.of("MDI"))
                .rolesToMatch(List.of("R"))
                .rolesToAssign(List.of("X", "Y"))
                .build());

        verify(roleService).assignRoleToApiCaseload("U1", "X");
        verify(roleService).assignRoleToApiCaseload("U1", "Y");
        verify(roleService).assignRoleToApiCaseload("U2", "X");
        verify(roleService).assignRoleToApiCaseload("U2", "Y");

        verify(roleService, atLeastOnce()).findUsersForPrisonHavingRole(any(), any());
        verifyNoMoreInteractions(roleService);
    }

    @Test
    public void removeRoles() {
        when(roleService.findUsersForPrisonHavingRole("MDI", "R")).thenReturn(Set.of("U1"));
        when(roleService.findUsersForPrisonHavingRole("MDI", "S")).thenReturn(Set.of("U2"));
        when(roleService.findUsersForPrisonHavingRole("LEI", "R")).thenReturn(Set.of());
        when(roleService.findUsersForPrisonHavingRole("LEI", "S")).thenReturn(Set.of("U2", "U3"));

        service.updateRoleAssignments(RoleAssignmentsSpecification.builder()
                .caseloads(List.of("MDI", "LEI"))
                .rolesToMatch(List.of("R", "S"))
                .rolesToRemove(List.of("X", "Y"))
                .build());


        verify(roleService).removeRole("U1", "MDI", "X");
        verify(roleService).removeRole("U1", "MDI", "Y");
        verify(roleService).removeRole("U2", "MDI", "X");
        verify(roleService).removeRole("U2", "MDI", "Y");

        verify(roleService).removeRole("U2", "LEI", "X");
        verify(roleService).removeRole("U2", "LEI", "Y");
        verify(roleService).removeRole("U3", "LEI", "X");
        verify(roleService).removeRole("U3", "LEI", "Y");

        verify(roleService, atLeastOnce()).findUsersForPrisonHavingRole(any(), any());
        verifyNoMoreInteractions(roleService);
    }

    @Test
    public void failureDuringAddRolesPreventsRemoveRoles() {
        when(roleService.findUsersForPrisonHavingRole("MDI", "A")).thenReturn(Set.of("U1", "U2"));

        doThrow(RuntimeException.class)
                .when(roleService).assignRoleToApiCaseload("U1", "Q");

        val expected = RoleAssignmentStats.builder()
                .caseload("MDI")
                .numMatchedUsers(2)
                .numAssignRoleSucceeded(4)
                .numAssignRoleFailed(2)
                .numUnassignRoleSucceeded(1)
                .numUnassignRoleIgnored(0)
                .numUnassignRoleFailed(0)
                .build();

        val results = service.updateRoleAssignments(RoleAssignmentsSpecification.builder()
                .caseloads(List.of("MDI"))
                .rolesToMatch(List.of("A"))
                .rolesToAssign(List.of("P", "Q", "R"))
                .rolesToRemove(List.of("X"))
                .build());

        assertThat(results).containsExactly(expected);

        verify(roleService, atMost(2)).assignRoleToApiCaseload(eq("U1"), anyString());

        verify(roleService).assignRoleToApiCaseload("U2", "P");
        verify(roleService).assignRoleToApiCaseload("U2", "Q");
        verify(roleService).assignRoleToApiCaseload("U2", "R");

        verify(roleService).removeRole("U2", "MDI", "X");

        verify(roleService, atLeastOnce()).findUsersForPrisonHavingRole(any(), any());

        verifyNoMoreInteractions(roleService);
    }

    @Test
    public void continueWhenRoleToRemoveNotFound() {
        when(roleService.findUsersForPrisonHavingRole("MDI", "A")).thenReturn(Set.of("U1"));

        doThrow(HttpClientErrorException.NotFound.class)
                .when(roleService).removeRole("U1", "MDI", "Y");

        val expected = RoleAssignmentStats.builder()
                .caseload("MDI")
                .numMatchedUsers(1)
                .numAssignRoleSucceeded(0)
                .numAssignRoleFailed(0)
                .numUnassignRoleSucceeded(2)
                .numUnassignRoleIgnored(1)
                .numUnassignRoleFailed(0)
                .build();

        val results = service.updateRoleAssignments(RoleAssignmentsSpecification.builder()
                .caseloads(List.of("MDI"))
                .rolesToMatch(List.of("A"))
                .rolesToRemove(List.of("X", "Y", "Z"))
                .build());

        assertThat(results).containsExactly(expected);

        verify(roleService).removeRole("U1", "MDI", "X");
        verify(roleService).removeRole("U1", "MDI", "Y");
        verify(roleService).removeRole("U1", "MDI", "Z");

        verify(roleService, atLeastOnce()).findUsersForPrisonHavingRole(any(), any());

        verifyNoMoreInteractions(roleService);
    }

    @Test
    public void continueWithNextUserWhenRemoveRoleFails() {
        when(roleService.findUsersForPrisonHavingRole("MDI", "A")).thenReturn(Set.of("U1", "U2", "U3"));

        doThrow(RuntimeException.class)
                .when(roleService).removeRole("U2", "MDI", "X");

        val expected = RoleAssignmentStats.builder()
                .caseload("MDI")
                .numMatchedUsers(3)
                .numAssignRoleSucceeded(0)
                .numAssignRoleFailed(0)
                .numUnassignRoleSucceeded(2)
                .numUnassignRoleIgnored(0)
                .numUnassignRoleFailed(1)
                .build();

        val results = service.updateRoleAssignments(RoleAssignmentsSpecification.builder()
                .caseloads(List.of("MDI"))
                .rolesToMatch(List.of("A"))
                .rolesToRemove(List.of("X"))
                .build());

        assertThat(results).containsExactly(expected);
        verify(roleService).removeRole("U1", "MDI", "X");
        verify(roleService).removeRole("U2", "MDI", "X");
        verify(roleService).removeRole("U3", "MDI", "X");

        verify(roleService, atLeastOnce()).findUsersForPrisonHavingRole(any(), any());

        verifyNoMoreInteractions(roleService);
    }

    @Test
    public void raisesTelemetryEvent() {
        when(roleService.findUsersForPrisonHavingRole("MDI", "A")).thenReturn(Set.of("U1", "U2", "U3"));

        doThrow(RuntimeException.class)
                .when(roleService).removeRole("U2", "MDI", "X");

        val expected = RoleAssignmentStats.builder()
                .caseload("MDI")
                .numMatchedUsers(3)
                .numAssignRoleSucceeded(0)
                .numAssignRoleFailed(0)
                .numUnassignRoleSucceeded(2)
                .numUnassignRoleIgnored(0)
                .numUnassignRoleFailed(1)
                .build();

        val results = service.updateRoleAssignments(RoleAssignmentsSpecification.builder()
                .caseloads(List.of("MDI"))
                .rolesToMatch(List.of("A"))
                .rolesToRemove(List.of("X"))
                .build());

        assertThat(results).containsExactly(expected);

        verify(telemetryClient).trackEvent("UpdateRollAssignment", expected.toMap(), null);
    }
}
