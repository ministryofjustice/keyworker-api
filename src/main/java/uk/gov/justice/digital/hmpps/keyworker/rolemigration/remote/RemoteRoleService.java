package uk.gov.justice.digital.hmpps.keyworker.rolemigration.remote;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import uk.gov.justice.digital.hmpps.keyworker.rolemigration.RoleService;
import uk.gov.justice.digital.hmpps.keyworker.services.RestCallHelper;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static uk.gov.justice.digital.hmpps.keyworker.services.RestCallHelpersKt.queryParamsOf;
import static uk.gov.justice.digital.hmpps.keyworker.services.RestCallHelpersKt.uriVariablesOf;

@Slf4j
@Component
public class RemoteRoleService implements RoleService {

    public static final String STAFF_ACCESS_CODES_LIST_URL = "/users/access-roles/caseload/{caseload}/access-role/{roleCode}";

    private static final ParameterizedTypeReference<List<String>> LIST_OF_USERNAME = new ParameterizedTypeReference<>() {
    };

    private final RestCallHelper restCallHelper;

    public RemoteRoleService(RestCallHelper restCallHelper) {
        this.restCallHelper = restCallHelper;
    }

    @Override
    public Set<String> findUsersForPrisonHavingRole(final String prisonId, final String roleCode) {
        log.info("Looking for users matching (prison {}, role {})", prisonId, roleCode);
        final var uriVariables = uriVariablesOf("caseload", prisonId, "roleCode", roleCode);
        final var responseEntity = restCallHelper.getEntity(STAFF_ACCESS_CODES_LIST_URL, queryParamsOf(), uriVariables, new ParameterizedTypeReference<List<String>>() {}, true);

        final var usernames = getUsernames(responseEntity);

        log.info("(prison {}, role {}) Found {} usernames: {}", prisonId, roleCode, usernames.size(), usernames);
        return usernames;

    }

    @Override
    public void removeRole(final String username, final String prisonId, final String roleCode) {
        log.info("Remove role association (username {}, prison {}, role {})", username, prisonId, roleCode);
        final var uriVariables = uriVariablesOf("username", username, "caseload", prisonId, "roleCode", roleCode);
        restCallHelper.delete("/users/{username}/caseload/{caseload}/access-role/{roleCode}", queryParamsOf(), uriVariables, true);
    }

    @Override
    public void assignRoleToApiCaseload(final String username, final String roleCode) {
        log.info("Assign (username {}, role {}) to the API caseload", username, roleCode);
        final var uriVariables = uriVariablesOf("username", username, "roleCode", roleCode);
        restCallHelper.put("/users/{username}/access-role/{roleCode}", queryParamsOf(), uriVariables, Void.class, true);
    }

    private static Set<String> getUsernames(final ResponseEntity<List<String>> responseEntity) {
        final var body = responseEntity.getBody();
        return body == null ? Set.of() : new TreeSet<>(body);
    }
}
