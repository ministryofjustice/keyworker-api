package uk.gov.justice.digital.hmpps.keyworker.rolemigration.remote;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import uk.gov.justice.digital.hmpps.keyworker.rolemigration.RoleService;
import uk.gov.justice.digital.hmpps.keyworker.services.RestCallHelper;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
public class RemoteRoleService implements RoleService {

    private static final ParameterizedTypeReference<List<StaffUserRoleDto>> LIST_OF_STAFF_USER_ROLE = new ParameterizedTypeReference<List<StaffUserRoleDto>>() {
    };

    private final RestTemplate restTemplate;

    @Autowired
    RemoteRoleService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public Set<Long> findStaffForPrisonHavingRole(String prisonId, String roleCode) {

        ResponseEntity<List<StaffUserRoleDto>> responseEntity = restTemplate.exchange(
                "/staff/access-roles/caseload/{caseload}/access-role/{roleCode}",
                HttpMethod.GET,
                null,
                LIST_OF_STAFF_USER_ROLE,
                prisonId,
                roleCode);

        Set<Long> staffIds = responseEntity.getBody().stream().map(StaffUserRoleDto::getStaffId).collect(Collectors.toSet());
        log.info("(prison {}, role {}) -> staffIds {}", prisonId, roleCode, staffIds);
        return staffIds;
    }

    @Override
    public void removeRole(long staffId, String prisonId, String roleCode) {
        log.info("Delete association (staffId {}, prison {}, role {})", staffId, prisonId, roleCode);
        restTemplate.delete(
                "/staff/{staffId}/access-roles/caseload/{caseload}/access-role/{roleCode}",
                staffId,
                prisonId,
                roleCode);
    }

    @Override
    public void assignRoleToApiCaseload(long staffId, String roleCode){
        log.info("Assign (staffId {}, role {}) to the API caseload", staffId, roleCode);
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
