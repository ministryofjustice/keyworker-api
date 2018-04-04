package uk.gov.justice.digital.hmpps.keyworker.rolemigration.remote;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import uk.gov.justice.digital.hmpps.keyworker.rolemigration.RoleService;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RemoteRoleService implements RoleService {

    private static final ParameterizedTypeReference<List<Long>> LIST_OF_LONG = new ParameterizedTypeReference<List<Long>>() {
    };

    private final RestTemplate restTemplate;

    public RemoteRoleService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public Set<Long> findStaffMatchingCaseloadAndRole(String caseloadId, String roleCode) {

        ResponseEntity<List<Long>> responseEntity = restTemplate.exchange(
                "/staff/access-roles/caseload/{caseload}/access-role/{roleCode}",
                HttpMethod.GET,
                null,
                LIST_OF_LONG,
                caseloadId,
                roleCode);
        return new HashSet<>(responseEntity.getBody());
    }

    public void removeRole(long staffId, String caseload, String roleCode) {
        restTemplate.delete(
                "/staff/{staffId}/access-roles/coaseload/{caseload}/access-role/{roleCode}",
                staffId,
                caseload,
                roleCode);
    }

    public void assignRole(long staffId, String caseload, String roleCode) {
        restTemplate.postForEntity(
                "/staff/{staffId}/access-roles/caseload{caseload}",
                roleCode,
                StaffUserRoleDto.class,
                staffId,
                caseload);
    }
}
