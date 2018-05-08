package uk.gov.justice.digital.hmpps.keyworker.rolemigration.remote;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import uk.gov.justice.digital.hmpps.keyworker.rolemigration.RoleService;
import uk.gov.justice.digital.hmpps.keyworker.services.RestCallHelper;

import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
public class RemoteRoleService implements RoleService {

    private static final ParameterizedTypeReference<List<StaffUserRoleDto>> LIST_OF_STAFF_USER_ROLE = new ParameterizedTypeReference<List<StaffUserRoleDto>>() {
    };
    public static final String STAFF_ACCESS_CODES_LIST_URL = "/users/access-roles/caseload/{caseload}/access-role/{roleCode}";

    private static final ParameterizedTypeReference<List<String>> LIST_OF_USERNAME = new ParameterizedTypeReference<List<String>>() {};

    private final RestTemplate restTemplate;


    @Autowired()
    RemoteRoleService(
            @Qualifier(value = "elite2ApiRestTemplate") RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public Set<String> findUsersForPrisonHavingRole(String prisonId, String roleCode) {
        log.info("Looking for users matching (prison {}, role {})", prisonId, roleCode);
        ResponseEntity<List<String>> responseEntity = restTemplate.exchange(
                STAFF_ACCESS_CODES_LIST_URL,
                HttpMethod.GET,
                null,
                LIST_OF_USERNAME,
                prisonId,
                roleCode);

        Set<String> usernames = new HashSet<>(responseEntity.getBody());
        log.info("(prison {}, role {}) -> usernames {}", prisonId, roleCode, usernames);
        return usernames;

    }

    @Override
    public void removeRole(String username, String prisonId, String roleCode) {
        log.info("Remove role association (username {}, prison {}, role {})", username, prisonId, roleCode);
        restTemplate.delete(
                "/users/{username}/caseload/{caseload}/access-role/{roleCode}",
                username,
                prisonId,
                roleCode);
    }

    @Override
    public void assignRoleToApiCaseload(String username, String roleCode) {
        log.info("Assign (username {}, role {}) to the API caseload", username, roleCode);

        restTemplate.put(
                "/users/{username}/access-role/{roleCode}",
                null,
                username,
                roleCode);

    }
}
