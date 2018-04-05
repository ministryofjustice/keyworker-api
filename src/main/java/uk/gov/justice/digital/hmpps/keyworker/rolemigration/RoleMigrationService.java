package uk.gov.justice.digital.hmpps.keyworker.rolemigration;


import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.function.BinaryOperator;
import java.util.stream.Stream;

@Slf4j
public class RoleMigrationService {

    private static final Set<String> SOURCE_ROLE_CODES;
    private static final Set<String>  TARGET_ROLE_CODES = Collections.singleton("KW_ADMIN");

    private static final BinaryOperator<Set<Long>> SET_INTERSECTION = (a, b) -> {
        Set<Long> result = new HashSet<>(a);
        result.retainAll(b);
        return result;
    };

    private final RoleService roleService;

    static {
        Set<String> codes = new HashSet<>(Arrays.asList("KW_ADMIN", "KEY_WORK"));
        SOURCE_ROLE_CODES = Collections.unmodifiableSet(codes);
    }

    public RoleMigrationService(RoleService roleService) {
        this.roleService = roleService;
    }

    public void migrate(String prisonId) {
        migrateRoles(prisonId, SOURCE_ROLE_CODES, TARGET_ROLE_CODES);
    }
    
    void migrateRoles(String prisonId, Set<String> sourceRoleCodes, Set<String> targetRoleCodes) {

        Set<Long> staffToMigrate = findStaffForPrisonHavingRoles(prisonId, sourceRoleCodes);

        log.info("Migrating {} staff for caseload {}", staffToMigrate.size(), prisonId);

        migrateRolesForStaffMembers(staffToMigrate, prisonId, sourceRoleCodes, targetRoleCodes);
    }

    private Set<Long> findStaffForPrisonHavingRoles(String prisonId, Set<String> roleCodes) {
        Stream<Set<Long>> staffIdSets = roleCodes.stream().map(roleCode -> roleService.findStaffForPrisonHavingRole(prisonId, roleCode));
        Optional<Set<Long>> staffIds = staffIdSets.reduce(SET_INTERSECTION);
        return staffIds.orElse(Collections.emptySet());
    }

    // TODO: If the there is a failure when sourceRoleCodes are partially removed a subsequent query for staff matching the set of roles will no longer match. Is this a problem?
    private void migrateRolesForStaffMembers(Set<Long> staffIds, String prisonId, Set<String> sourceRoleCodes, Set<String> targetRoleCodes) {
        staffIds.forEach(staffId -> {
            targetRoleCodes.forEach(roleCode -> roleService.assignRoleToApiCaseload(staffId, roleCode));
            sourceRoleCodes.forEach(roleCode -> roleService.removeRole(staffId, prisonId, roleCode));
        });
    }
}
