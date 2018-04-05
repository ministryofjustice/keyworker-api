package uk.gov.justice.digital.hmpps.keyworker.rolemigration.remote;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import uk.gov.justice.digital.hmpps.keyworker.rolemigration.RoleService;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class RemoteRoleService implements RoleService {

    private static final ParameterizedTypeReference<List<StaffUserRoleDto>> LIST_OF_STAFF_USER_ROLE = new ParameterizedTypeReference<List<StaffUserRoleDto>>() {
    };

    private final RestTemplate restTemplate;

    public RemoteRoleService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public Set<Long> findStaffMatchingCaseloadAndRole(String caseloadId, String roleCode) {

        ResponseEntity<List<StaffUserRoleDto>> responseEntity = restTemplate.exchange(
                "/staff/access-roles/caseload/{caseload}/access-role/{roleCode}",
                HttpMethod.GET,
                null,
                LIST_OF_STAFF_USER_ROLE,
                caseloadId,
                roleCode);

        return responseEntity.getBody().stream().map(StaffUserRoleDto::getStaffId).collect(Collectors.toSet());
    }

    @Override
    public void removeRole(long staffId, String caseload, String roleCode) {
        restTemplate.delete(
                "/staff/{staffId}/access-roles/caseload/{caseload}/access-role/{roleCode}",
                staffId,
                caseload,
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
