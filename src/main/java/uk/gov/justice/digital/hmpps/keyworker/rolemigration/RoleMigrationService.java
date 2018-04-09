package uk.gov.justice.digital.hmpps.keyworker.rolemigration;


import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpServerErrorException;

import java.util.*;
import java.util.function.BinaryOperator;
import java.util.stream.Collectors;

@Slf4j
@Service
public class RoleMigrationService {

    private static final BinaryOperator<Set<Long>> SET_UNION = (a,b) -> {
        Set<Long> result = new HashSet<>(a);
        result.addAll(b);
        return result;
    };

    private final RoleService roleService;
    private final Set<String> rolesToMatch;
    private final Set<String> rolesToAssign;

    public RoleMigrationService(RoleService roleService, RoleMigrationConfiguration configuration) {
        this.roleService = roleService;
        rolesToMatch = Collections.unmodifiableSet(new HashSet<>(configuration.getRolesToMatch()));
        rolesToAssign = Collections.unmodifiableSet(new HashSet<>(configuration.getRolesToAssign()));
    }

    public void migrate(String prisonId) {
        migrateRoles(prisonId, rolesToMatch, rolesToAssign);
    }

    private void migrateRoles(String prisonId, Set<String> rolesToMatch, Set<String> rolesToAssign) {

        List<Pair<String, Set<Long>>> staffIdsByRole = findStaffIdsByRole(prisonId, rolesToMatch);

        assignRolesToStaff(rolesToAssign, allStaffIds(staffIdsByRole));

        removeRolesFromStaff(prisonId, staffIdsByRole);
    }

    /**
     * Use RoleService#findStaffIdsByRole to retrieve Sets of staffId for each roleCode. Return the
     * results as Pairs of (roleCode, set of staffId) Searches are restricted to the set of staff serving a prison, also
     * known as a caseload.
     * @param prisonId The prison/caseload identifier for which staffIds are required
     * @param roleCodes The set of role codes to use to group staff
     * @return a list of pairs of (roleCode, set of staffId)
     */
    private List<Pair<String, Set<Long>>> findStaffIdsByRole(String prisonId, Set<String> roleCodes) {
        return roleCodes
                .stream()
                .map( sourceRole -> Pair.of(
                        sourceRole,
                        roleService.findStaffForPrisonHavingRole(prisonId, sourceRole)))
                .collect(Collectors.toList());
    }

    /**
     * Extract the set of all staffIds contained in the supplied data structure.
     * @param staffIdsByRole staffIds grouped by role membership.
     * @return all the supplied staffIds as a set.
     */
    private Set<Long> allStaffIds(List<Pair<String, Set<Long>>> staffIdsByRole) {
        return staffIdsByRole.stream().map(Pair::getRight).reduce(new HashSet<>(), SET_UNION);
    }

    /**
     * Assign the supplied roles to all the staff represented by the supplied set of staffId. This is an assignment
     * to the 'API' caseload.
     * @param rolesToAssign The set of role codes to assign to the staff.
     * @param staffIds The set of staffId to which the assignments should be made.
     */
    private void assignRolesToStaff(Set<String> rolesToAssign, Set<Long> staffIds) {
        staffIds.forEach(staffId ->
            rolesToAssign.forEach(roleToAssign -> {
                try {
                    roleService.assignRoleToApiCaseload(staffId, roleToAssign);
                } catch (HttpServerErrorException e){
                    log.info("API caseload role assignment (staffId {}, role {}) failed. (Does the role assignment already exist?).", staffId, roleToAssign);
                }
            }));
    }

    /**
     * Remove the all the role associations (prisonId, roleCode, staffId) represented by the supplied
     * data structure.
     * @param prisonId The prison / caseload
     * @param staffIdsByRole sets of staffId by role code.
     */
    private void removeRolesFromStaff(String prisonId, List<Pair<String, Set<Long>>> staffIdsByRole) {
        staffIdsByRole.forEach(roleAndStaffIds ->
                roleAndStaffIds.getRight().forEach(staffId ->
                        roleService.removeRole(staffId, prisonId, roleAndStaffIds.getLeft())));
    }
}
