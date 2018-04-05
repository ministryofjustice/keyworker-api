package uk.gov.justice.digital.hmpps.keyworker.rolemigration;


import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.BinaryOperator;
import java.util.stream.Collectors;

@Slf4j
@Service
public class RoleMigrationService {

    private static final Set<String> SOURCE_ROLE_CODES;
    private static final Set<String>  TARGET_ROLE_CODES = Collections.singleton("KW_ADMIN");

    private static final BinaryOperator<Set<Long>> SET_UNION = (a,b) -> {
        Set<Long> result = new HashSet<>(a);
        result.addAll(b);
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

        List<Pair<String, Set<Long>>> staffIdsByRole = findStaffIdsByRole(prisonId, sourceRoleCodes);

        assignRolesToStaff(targetRoleCodes, allStaffIds(staffIdsByRole));

        removeRolesFromStaff(prisonId, staffIdsByRole);
    }

    private List<Pair<String, Set<Long>>> findStaffIdsByRole(String prisonId, Set<String> sourceRoleCodes) {
        return sourceRoleCodes
                .stream()
                .map( sourceRole -> Pair.of(
                        sourceRole,
                        roleService.findStaffForPrisonHavingRole(prisonId, sourceRole)))
                .collect(Collectors.toList());
    }

    private Set<Long> allStaffIds(List<Pair<String, Set<Long>>> staffIdsByRole) {
        return staffIdsByRole.stream().map(Pair::getRight).reduce(new HashSet<>(), SET_UNION);
    }

    private void assignRolesToStaff(Set<String> rolesToAssign, Set<Long> staffIds) {
        staffIds.forEach(staffId ->
            rolesToAssign.forEach(targetRole ->
                    roleService.assignRoleToApiCaseload(staffId, targetRole)));
    }

    private void removeRolesFromStaff(String prisonId, List<Pair<String, Set<Long>>> staffIdsByRole) {
        staffIdsByRole.forEach(roleAndStaffIds ->
                roleAndStaffIds.getRight().forEach(staffId ->
                        roleService.removeRole(staffId, prisonId, roleAndStaffIds.getLeft())));
    }
}
