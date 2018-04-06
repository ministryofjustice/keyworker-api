package uk.gov.justice.digital.hmpps.keyworker.rolemigration;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.*;

import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class RoleMigrationServiceTest {
    private static final String SOURCE_ROLE_1 = "SR1";
    private static final String SOURCE_ROLE_2 = "SR2";

    private static final String TARGET_ROLE_1 = "TR1";

    private static final String PRISON_ID = "TC1";

    private static final List<String> SINGLE_SOURCE_ROLE = Collections.singletonList(SOURCE_ROLE_1);
    private static final List<String> TWO_SOURCE_ROLES = Arrays.asList(SOURCE_ROLE_1, SOURCE_ROLE_2);

    private static final List<String> SINGLE_TARGET_ROLE = Collections.singletonList(TARGET_ROLE_1);

    private static final List<String> NO_ROLES = Collections.emptyList();

    @Mock
    private RoleService roleService;

//    @InjectMocks
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

        initialiseService( SINGLE_SOURCE_ROLE,SINGLE_TARGET_ROLE);
        service.migrate(PRISON_ID);

        verify(roleService).findStaffForPrisonHavingRole(PRISON_ID, SOURCE_ROLE_1);
        verifyNoMoreInteractions(roleService);
    }

    @Test
    public void givenMatchingStaffThenSingleSourceRoleIsRemoved() {
        when(roleService.findStaffForPrisonHavingRole(any(), any())).thenReturn(setOf(1L));

        initialiseService(SINGLE_SOURCE_ROLE, NO_ROLES);
        service.migrate(PRISON_ID);

        verify(roleService).removeRole(1L, PRISON_ID, SOURCE_ROLE_1);
    }

    @Test
    public void givenMatchingStaffThenSingleSourceRolesAreRemoved() {
        when(roleService.findStaffForPrisonHavingRole(any(), any())).thenReturn(setOf(1L));

        initialiseService(TWO_SOURCE_ROLES, NO_ROLES);
        service.migrate(PRISON_ID);

        verify(roleService).removeRole(1L, PRISON_ID, SOURCE_ROLE_1);
        verify(roleService).removeRole(1L, PRISON_ID, SOURCE_ROLE_2);
    }

    @Test
    public void givenOverlappingStaffPerRoleThenAllRolesAreRemoved() {
        when(roleService.findStaffForPrisonHavingRole(PRISON_ID, SOURCE_ROLE_1)).thenReturn(setOf(1L, 2L));
        when(roleService.findStaffForPrisonHavingRole(PRISON_ID, SOURCE_ROLE_2)).thenReturn(setOf(2L ,3L));

        initialiseService(TWO_SOURCE_ROLES, NO_ROLES);
        service.migrate(PRISON_ID);

        verify(roleService).findStaffForPrisonHavingRole(PRISON_ID, SOURCE_ROLE_1);
        verify(roleService).findStaffForPrisonHavingRole(PRISON_ID, SOURCE_ROLE_2);


        verify(roleService).removeRole(1L, PRISON_ID, SOURCE_ROLE_1);
        verify(roleService).removeRole(2L, PRISON_ID, SOURCE_ROLE_1);
        verify(roleService).removeRole(2L, PRISON_ID, SOURCE_ROLE_2);
        verify(roleService).removeRole(3L, PRISON_ID, SOURCE_ROLE_2);

        verifyNoMoreInteractions(roleService);
    }

    @Test
    public void givenOverlappingStaffPerRoleThenTargetRolesAreAssigned() {
        when(roleService.findStaffForPrisonHavingRole(PRISON_ID, SOURCE_ROLE_1)).thenReturn(setOf(1L, 2L));
        when(roleService.findStaffForPrisonHavingRole(PRISON_ID, SOURCE_ROLE_2)).thenReturn(setOf(2L ,3L));

        initialiseService(TWO_SOURCE_ROLES, SINGLE_TARGET_ROLE);
        service.migrate(PRISON_ID);

        verify(roleService).findStaffForPrisonHavingRole(PRISON_ID, SOURCE_ROLE_1);
        verify(roleService).findStaffForPrisonHavingRole(PRISON_ID, SOURCE_ROLE_2);

        verify(roleService).removeRole(1L, PRISON_ID, SOURCE_ROLE_1);
        verify(roleService).removeRole(2L, PRISON_ID, SOURCE_ROLE_1);
        verify(roleService).removeRole(2L, PRISON_ID, SOURCE_ROLE_2);
        verify(roleService).removeRole(3L, PRISON_ID, SOURCE_ROLE_2);

        verify(roleService).assignRoleToApiCaseload(1L, TARGET_ROLE_1);
        verify(roleService).assignRoleToApiCaseload(2L, TARGET_ROLE_1);
        verify(roleService).assignRoleToApiCaseload(3L, TARGET_ROLE_1);

        verifyNoMoreInteractions(roleService);
    }

    @Test
    public void givenMatchingStaffThenSingleTargetRolesAreAdded() {
        when(roleService.findStaffForPrisonHavingRole(any(), any())).thenReturn(setOf(1L));

        initialiseService(SINGLE_SOURCE_ROLE, SINGLE_TARGET_ROLE);
        service.migrate(PRISON_ID);

        verify(roleService).assignRoleToApiCaseload(1L, TARGET_ROLE_1);
    }

    private Set<Long> setOf(Long... ids) {
        return new HashSet<>(Arrays.asList(ids));
    }

    private void initialiseService(List<String> sourceRoles, List<String> targetRoles) {
        service = new RoleMigrationService(roleService, configuration(sourceRoles, targetRoles));
    }

    private RoleMigrationConfiguration configuration(List<String> sourceRoles, List<String> targetRoles) {
        RoleMigrationConfiguration configuration = new RoleMigrationConfiguration();
        configuration.setSourceRoles(sourceRoles);
        configuration.setTargetRoles(targetRoles);
        return configuration;
    }
}
