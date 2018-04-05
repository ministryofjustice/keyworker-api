package uk.gov.justice.digital.hmpps.keyworker.rolemigration;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class RoleMigrationServiceTest {
    private static final String SOURCE_ROLE_1 = "SR1";
    private static final String SOURCE_ROLE_2 = "SR2";

    private static final String TARGET_ROLE_1 = "TR1";

    private static final String PRISON_ID = "TC1";

    private static final Set<String> SINGLE_SOURCE_ROLE = Collections.singleton(SOURCE_ROLE_1);
    private static final Set<String> TWO_SOURCE_ROLES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(SOURCE_ROLE_1, SOURCE_ROLE_2)));

    private static final Set<String> SINGLE_TARGET_ROLE = Collections.singleton(TARGET_ROLE_1);

    private static final Set<String> NO_ROLES = Collections.emptySet();

    @Mock
    private RoleService roleService;

    @InjectMocks
    private RoleMigrationService service;

    @Test
    public void givenNoRolesToMigrateThenDoNothing() {
        service.migrateRoles(PRISON_ID, NO_ROLES, NO_ROLES);
        verifyZeroInteractions(roleService);
    }

    @Test
    public void givenNoStaffToMigrateThenOnlySearchForStaff() {
        when(roleService.findStaffForPrisonHavingRole(any(), any())).thenReturn(setOf());

        service.migrateRoles(PRISON_ID, SINGLE_SOURCE_ROLE,SINGLE_TARGET_ROLE);

        verify(roleService).findStaffForPrisonHavingRole(PRISON_ID, SOURCE_ROLE_1);
        verifyNoMoreInteractions(roleService);
    }

    @Test
    public void givenMatchingStaffThenSingleSourceRoleIsRemoved() {
        when(roleService.findStaffForPrisonHavingRole(any(), any())).thenReturn(setOf(1L));

        service.migrateRoles(PRISON_ID, SINGLE_SOURCE_ROLE, NO_ROLES);

        verify(roleService).removeRole(1L, PRISON_ID, SOURCE_ROLE_1);
    }

    @Test
    public void givenMatchingStaffThenSingleSourceRolesAreRemoved() {
        when(roleService.findStaffForPrisonHavingRole(any(), any())).thenReturn(setOf(1L));

        service.migrateRoles(PRISON_ID, TWO_SOURCE_ROLES, NO_ROLES);

        verify(roleService).removeRole(1L, PRISON_ID, SOURCE_ROLE_1);
        verify(roleService).removeRole(1L, PRISON_ID, SOURCE_ROLE_2);
    }

    @Test
    public void givenOverlappingStaffPerRoleThenAllRolesAreRemoved() {
        when(roleService.findStaffForPrisonHavingRole(PRISON_ID, SOURCE_ROLE_1)).thenReturn(setOf(1L, 2L));
        when(roleService.findStaffForPrisonHavingRole(PRISON_ID, SOURCE_ROLE_2)).thenReturn(setOf(2L ,3L));

        service.migrateRoles(PRISON_ID, TWO_SOURCE_ROLES, NO_ROLES);

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

        service.migrateRoles(PRISON_ID, TWO_SOURCE_ROLES, SINGLE_TARGET_ROLE);

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

        service.migrateRoles(PRISON_ID, SINGLE_SOURCE_ROLE, SINGLE_TARGET_ROLE);

        verify(roleService).assignRoleToApiCaseload(1L, TARGET_ROLE_1);
    }

    private Set<Long> setOf(Long... ids) {
        return new HashSet<>(Arrays.asList(ids));
    }
}
