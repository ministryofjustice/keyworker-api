package uk.gov.justice.digital.hmpps.keyworker.rolemigration.remote;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import uk.gov.justice.digital.hmpps.keyworker.rolemigration.RoleService;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

@Slf4j
// TODO DT-611 Use RestCallHelper
@Component
public class RemoteRoleService implements RoleService {

    public static final String STAFF_ACCESS_CODES_LIST_URL = "/users/access-roles/caseload/{caseload}/access-role/{roleCode}";

    private static final ParameterizedTypeReference<List<String>> LIST_OF_USERNAME = new ParameterizedTypeReference<>() {
    };

    private final WebClient oauth2WebClient;


    @Autowired()
    RemoteRoleService(final WebClient oauth2WebClient) {
        this.oauth2WebClient = oauth2WebClient;
    }

    @Override
    public Set<String> findUsersForPrisonHavingRole(final String prisonId, final String roleCode) {
        log.info("Looking for users matching (prison {}, role {})", prisonId, roleCode);
        final var responseEntity = oauth2WebClient
                .get()
                .uri(uriBuilder -> uriBuilder.path(STAFF_ACCESS_CODES_LIST_URL).build(prisonId, roleCode))
                .retrieve()
                .toEntity(new ParameterizedTypeReference<List<String>>() {
                })
                .block();

        final var usernames = getUsernames(responseEntity);

        log.info("(prison {}, role {}) Found {} usernames: {}", prisonId, roleCode, usernames.size(), usernames);
        return usernames;

    }

    @Override
    public void removeRole(final String username, final String prisonId, final String roleCode) {
        log.info("Remove role association (username {}, prison {}, role {})", username, prisonId, roleCode);
        oauth2WebClient
                .delete()
                .uri(uriBuilder -> uriBuilder
                        .path("/users/{username}/caseload/{caseload}/access-role/{roleCode}")
                        .build(username, prisonId, roleCode))
                .exchange()
                .block();
    }

    @Override
    public void assignRoleToApiCaseload(final String username, final String roleCode) {
        log.info("Assign (username {}, role {}) to the API caseload", username, roleCode);

        oauth2WebClient
                .put()
                .uri(uriBuilder -> uriBuilder
                        .path("/users/{username}/access-role/{roleCode}")
                        .build(username, roleCode))
                .exchange()
                .block();
    }

    private static Set<String> getUsernames(final ResponseEntity<List<String>> responseEntity) {
        final var body = responseEntity.getBody();
        return body == null ? Set.of() : new TreeSet<>(body);
    }
}
