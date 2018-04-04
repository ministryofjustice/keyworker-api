package uk.gov.justice.digital.hmpps.keyworker.rolemigration;

import java.util.Set;

public interface RoleService {

    Set<Long> findStaffMatchingCaseloadAndRole(String caseloadId, String roleCode);

    void removeRole(long staffId, String caseload, String roleCode);

    void assignRole(long staffId, String caseload, String roleCode);
}
