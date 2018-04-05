package uk.gov.justice.digital.hmpps.keyworker.rolemigration.remote;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import uk.gov.justice.digital.hmpps.keyworker.rolemigration.RoleService;
import uk.gov.justice.digital.hmpps.keyworker.services.Elite2ApiSource;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RemoteRoleService extends Elite2ApiSource implements RoleService {

    private static final ParameterizedTypeReference<List<StaffUserRoleDto>> LIST_OF_STAFF_USER_ROLE = new ParameterizedTypeReference<List<StaffUserRoleDto>>() {
    };

    @Override
    public Set<Long> findStaffForPrisonHavingRole(String prisonId, String roleCode) {

        ResponseEntity<List<StaffUserRoleDto>> responseEntity = restTemplate.exchange(
                "/staff/access-roles/caseload/{caseload}/access-role/{roleCode}",
                HttpMethod.GET,
                null,
                LIST_OF_STAFF_USER_ROLE,
                prisonId,
                roleCode);

        return responseEntity.getBody().stream().map(StaffUserRoleDto::getStaffId).collect(Collectors.toSet());
    }

    @Override
    public void removeRole(long staffId, String prisonId, String roleCode) {
        restTemplate.delete(
                "/staff/{staffId}/access-roles/caseload/{caseload}/access-role/{roleCode}",
                staffId,
                prisonId,
                roleCode);
    }

    @Override
    public void assignRoleToApiCaseload(long staffId, String roleCode){
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(roleCode, headers);

        restTemplate.exchange(
                "/staff/{staffId}/access-roles/",
                HttpMethod.POST,
                entity,
                String.class,
                staffId);
    }
}
