package uk.gov.justice.digital.hmpps.keyworker.rolemigration;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.*;

import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class UserRolesMigrationServiceTest {
    private static final String ROLE_TO_MATCH_1 = "SR1";
    private static final String ROLE_TO_MATCH_2 = "SR2";

    private static final String ROLE_TO_MIGRATE_1 = "SR2";
    private static final String ROLE_TO_ASSIGN_1 = "TR1";

    private static final String PRISON_ID = "TC1";

    private static final String USERNAME_1 = "UN1";
    private static final String USERNAME_2 = "UN2";
    private static final String USERNAME_3 = "UN3";

    private static final List<String> SINGLE_ROLE_TO_MATCH = Collections.singletonList(ROLE_TO_MATCH_1);
    private static final List<String> SINGLE_ROLE_TO_MATCH_2 = Collections.singletonList(ROLE_TO_MATCH_2);
    private static final List<String> TWO_ROLES_TO_MATCH = Arrays.asList(ROLE_TO_MATCH_1, ROLE_TO_MATCH_2);

    private static final List<String> SINGLE_ROLE_TO_ASSIGN = Collections.singletonList(ROLE_TO_ASSIGN_1);
    private static final List<String> SINGLE_ROLE_TO_MIGRATE = Collections.singletonList(ROLE_TO_MIGRATE_1);

    private static final List<String> NO_ROLES = Collections.emptyList();

    @Mock
    private RoleService roleService;

    private UserRolesMigrationService service;

    @Test
    public void givenNoRolesToMigrateThenDoNothing() {
        initialiseService(NO_ROLES, NO_ROLES, SINGLE_ROLE_TO_MIGRATE);
        service.migrate(PRISON_ID);
        verifyZeroInteractions(roleService);
    }

    @Test
    public void givenNoStaffToMigrateThenOnlySearchForStaff() {
        when(roleService.findUsersForPrisonHavingRole(any(), any())).thenReturn(setOf());

        initialiseService(SINGLE_ROLE_TO_MATCH, SINGLE_ROLE_TO_ASSIGN, SINGLE_ROLE_TO_MIGRATE);
        service.migrate(PRISON_ID);

        verify(roleService).findUsersForPrisonHavingRole(PRISON_ID, ROLE_TO_MATCH_1);
        verifyNoMoreInteractions(roleService);
    }

    @Test
    public void givenMatchingStaffThenSingleMatchingRoleIsRemoved() {
        when(roleService.findUsersForPrisonHavingRole(any(), any())).thenReturn(setOf(USERNAME_1));

        initialiseService(SINGLE_ROLE_TO_MATCH, NO_ROLES, SINGLE_ROLE_TO_MIGRATE);
        service.migrate(PRISON_ID);

        verify(roleService).removeRole(USERNAME_1, PRISON_ID, ROLE_TO_MATCH_1);
    }

    @Test
    public void givenMatchingStaffThenMatchingRolesAreRemoved() {
        when(roleService.findUsersForPrisonHavingRole(any(), any())).thenReturn(setOf(USERNAME_1));

        initialiseService(TWO_ROLES_TO_MATCH, NO_ROLES, SINGLE_ROLE_TO_MIGRATE);
        service.migrate(PRISON_ID);

        verify(roleService).removeRole(USERNAME_1, PRISON_ID, ROLE_TO_MATCH_1);
        verify(roleService).removeRole(USERNAME_1, PRISON_ID, ROLE_TO_MATCH_2);
    }

    @Test
    public void givenOverlappingStaffPerRoleThenAllMatchingRolesAreRemoved() {
        when(roleService.findUsersForPrisonHavingRole(PRISON_ID, ROLE_TO_MATCH_1)).thenReturn(setOf(USERNAME_1, USERNAME_2));
        when(roleService.findUsersForPrisonHavingRole(PRISON_ID, ROLE_TO_MATCH_2)).thenReturn(setOf(USERNAME_2 ,USERNAME_3));

        initialiseService(TWO_ROLES_TO_MATCH, NO_ROLES, SINGLE_ROLE_TO_MIGRATE);
        service.migrate(PRISON_ID);

        verify(roleService).findUsersForPrisonHavingRole(PRISON_ID, ROLE_TO_MATCH_1);
        verify(roleService).findUsersForPrisonHavingRole(PRISON_ID, ROLE_TO_MATCH_2);


        verify(roleService).removeRole(USERNAME_1, PRISON_ID, ROLE_TO_MATCH_1);
        verify(roleService).removeRole(USERNAME_2, PRISON_ID, ROLE_TO_MATCH_1);
        verify(roleService).removeRole(USERNAME_2, PRISON_ID, ROLE_TO_MATCH_2);
        verify(roleService).removeRole(USERNAME_3, PRISON_ID, ROLE_TO_MATCH_2);

        verifyNoMoreInteractions(roleService);
    }

    @Test
    public void givenOverlappingStaffPerRoleThenRolesAreAssigned() {
        when(roleService.findUsersForPrisonHavingRole(PRISON_ID, ROLE_TO_MATCH_1)).thenReturn(setOf(USERNAME_1, USERNAME_2));
        when(roleService.findUsersForPrisonHavingRole(PRISON_ID, ROLE_TO_MATCH_2)).thenReturn(setOf(USERNAME_2 ,USERNAME_3));

        initialiseService(TWO_ROLES_TO_MATCH, SINGLE_ROLE_TO_ASSIGN, SINGLE_ROLE_TO_MIGRATE);
        service.migrate(PRISON_ID);

        verify(roleService).findUsersForPrisonHavingRole(PRISON_ID, ROLE_TO_MATCH_1);
        verify(roleService).findUsersForPrisonHavingRole(PRISON_ID, ROLE_TO_MATCH_2);

        verify(roleService).removeRole(USERNAME_1, PRISON_ID, ROLE_TO_MATCH_1);
        verify(roleService).removeRole(USERNAME_2, PRISON_ID, ROLE_TO_MATCH_1);
        verify(roleService).removeRole(USERNAME_2, PRISON_ID, ROLE_TO_MATCH_2);
        verify(roleService).removeRole(USERNAME_3, PRISON_ID, ROLE_TO_MATCH_2);

        verify(roleService).assignRoleToApiCaseload(USERNAME_2, ROLE_TO_ASSIGN_1);
        verify(roleService).assignRoleToApiCaseload(USERNAME_3, ROLE_TO_ASSIGN_1);

        verifyNoMoreInteractions(roleService);
    }

    @Test
    public void givenMatchingStaffThenSingleRoleIsAdded() {
        when(roleService.findUsersForPrisonHavingRole(any(), any())).thenReturn(setOf(USERNAME_1));

        initialiseService(SINGLE_ROLE_TO_MATCH_2, SINGLE_ROLE_TO_ASSIGN, SINGLE_ROLE_TO_MIGRATE);
        service.migrate(PRISON_ID);

        verify(roleService).assignRoleToApiCaseload(USERNAME_1, ROLE_TO_ASSIGN_1);
    }

    private Set<String> setOf(String... usernames) {
        return new HashSet<>(Arrays.asList(usernames));
    }

    private void initialiseService(List<String> sourceRoles, List<String> targetRoles, List<String> migrateRoles) {
        service = new UserRolesMigrationService(roleService, configuration(sourceRoles, targetRoles, migrateRoles));
    }

    private RoleMigrationConfiguration configuration(List<String> sourceRoles, List<String> targetRoles, List<String> migrateRoles) {
        RoleMigrationConfiguration configuration = new RoleMigrationConfiguration();
        configuration.setRolesToMatch(sourceRoles);
        configuration.setRolesToAssign(targetRoles);
        configuration.setRolesToMigrate(migrateRoles);
        return configuration;
    }
}
