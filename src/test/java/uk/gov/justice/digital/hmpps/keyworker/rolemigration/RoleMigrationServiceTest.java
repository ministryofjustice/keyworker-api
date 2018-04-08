package uk.gov.justice.digital.hmpps.keyworker.rolemigration;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.*;

import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class RoleMigrationServiceTest {
    private static final String ROLE_TO_MATCH_1 = "SR1";
    private static final String ROLE_TO_MATCH_2 = "SR2";

    private static final String ROLE_TO_ASSIGN_1 = "TR1";

    private static final String PRISON_ID = "TC1";

    private static final List<String> SINGLE_ROLE_TO_MATCH = Collections.singletonList(ROLE_TO_MATCH_1);
    private static final List<String> TWO_ROLES_TO_MATCH = Arrays.asList(ROLE_TO_MATCH_1, ROLE_TO_MATCH_2);

    private static final List<String> SINGLE_ROLE_TO_ASSIGN = Collections.singletonList(ROLE_TO_ASSIGN_1);

    private static final List<String> NO_ROLES = Collections.emptyList();

    @Mock
    private RoleService roleService;

    private RoleMigrationService service;

    @Test
    public void givenNoRolesToMigrateThenDoNothing() {
        initialiseService(NO_ROLES, NO_ROLES);
        service.migrate(PRISON_ID);
        verifyZeroInteractions(roleService);
    }

    @Test
    public void givenNoStaffToMigrateThenOnlySearchForStaff() {
        when(roleService.findStaffForPrisonHavingRole(any(), any())).thenReturn(setOf());

        initialiseService(SINGLE_ROLE_TO_MATCH, SINGLE_ROLE_TO_ASSIGN);
        service.migrate(PRISON_ID);

        verify(roleService).findStaffForPrisonHavingRole(PRISON_ID, ROLE_TO_MATCH_1);
        verifyNoMoreInteractions(roleService);
    }

    @Test
    public void givenMatchingStaffThenSingleMatchingRoleIsRemoved() {
        when(roleService.findStaffForPrisonHavingRole(any(), any())).thenReturn(setOf(1L));

        initialiseService(SINGLE_ROLE_TO_MATCH, NO_ROLES);
        service.migrate(PRISON_ID);

        verify(roleService).removeRole(1L, PRISON_ID, ROLE_TO_MATCH_1);
    }

    @Test
    public void givenMatchingStaffThenMatchingRolesAreRemoved() {
        when(roleService.findStaffForPrisonHavingRole(any(), any())).thenReturn(setOf(1L));

        initialiseService(TWO_ROLES_TO_MATCH, NO_ROLES);
        service.migrate(PRISON_ID);

        verify(roleService).removeRole(1L, PRISON_ID, ROLE_TO_MATCH_1);
        verify(roleService).removeRole(1L, PRISON_ID, ROLE_TO_MATCH_2);
    }

    @Test
    public void givenOverlappingStaffPerRoleThenAllMatchingRolesAreRemoved() {
        when(roleService.findStaffForPrisonHavingRole(PRISON_ID, ROLE_TO_MATCH_1)).thenReturn(setOf(1L, 2L));
        when(roleService.findStaffForPrisonHavingRole(PRISON_ID, ROLE_TO_MATCH_2)).thenReturn(setOf(2L ,3L));

        initialiseService(TWO_ROLES_TO_MATCH, NO_ROLES);
        service.migrate(PRISON_ID);

        verify(roleService).findStaffForPrisonHavingRole(PRISON_ID, ROLE_TO_MATCH_1);
        verify(roleService).findStaffForPrisonHavingRole(PRISON_ID, ROLE_TO_MATCH_2);


        verify(roleService).removeRole(1L, PRISON_ID, ROLE_TO_MATCH_1);
        verify(roleService).removeRole(2L, PRISON_ID, ROLE_TO_MATCH_1);
        verify(roleService).removeRole(2L, PRISON_ID, ROLE_TO_MATCH_2);
        verify(roleService).removeRole(3L, PRISON_ID, ROLE_TO_MATCH_2);

        verifyNoMoreInteractions(roleService);
    }

    @Test
    public void givenOverlappingStaffPerRoleThenRolesAreAssigned() {
        when(roleService.findStaffForPrisonHavingRole(PRISON_ID, ROLE_TO_MATCH_1)).thenReturn(setOf(1L, 2L));
        when(roleService.findStaffForPrisonHavingRole(PRISON_ID, ROLE_TO_MATCH_2)).thenReturn(setOf(2L ,3L));

        initialiseService(TWO_ROLES_TO_MATCH, SINGLE_ROLE_TO_ASSIGN);
        service.migrate(PRISON_ID);

        verify(roleService).findStaffForPrisonHavingRole(PRISON_ID, ROLE_TO_MATCH_1);
        verify(roleService).findStaffForPrisonHavingRole(PRISON_ID, ROLE_TO_MATCH_2);

        verify(roleService).removeRole(1L, PRISON_ID, ROLE_TO_MATCH_1);
        verify(roleService).removeRole(2L, PRISON_ID, ROLE_TO_MATCH_1);
        verify(roleService).removeRole(2L, PRISON_ID, ROLE_TO_MATCH_2);
        verify(roleService).removeRole(3L, PRISON_ID, ROLE_TO_MATCH_2);

        verify(roleService).assignRoleToApiCaseload(1L, ROLE_TO_ASSIGN_1);
        verify(roleService).assignRoleToApiCaseload(2L, ROLE_TO_ASSIGN_1);
        verify(roleService).assignRoleToApiCaseload(3L, ROLE_TO_ASSIGN_1);

        verifyNoMoreInteractions(roleService);
    }

    @Test
    public void givenMatchingStaffThenSingleRoleIsAdded() {
        when(roleService.findStaffForPrisonHavingRole(any(), any())).thenReturn(setOf(1L));

        initialiseService(SINGLE_ROLE_TO_MATCH, SINGLE_ROLE_TO_ASSIGN);
        service.migrate(PRISON_ID);

        verify(roleService).assignRoleToApiCaseload(1L, ROLE_TO_ASSIGN_1);
    }

    private Set<Long> setOf(Long... ids) {
        return new HashSet<>(Arrays.asList(ids));
    }

    private void initialiseService(List<String> sourceRoles, List<String> targetRoles) {
        service = new RoleMigrationService(roleService, configuration(sourceRoles, targetRoles));
    }

    private RoleMigrationConfiguration configuration(List<String> sourceRoles, List<String> targetRoles) {
        RoleMigrationConfiguration configuration = new RoleMigrationConfiguration();
        configuration.setRolesToMatch(sourceRoles);
        configuration.setRolesToAssign(targetRoles);
        return configuration;
    }
}
