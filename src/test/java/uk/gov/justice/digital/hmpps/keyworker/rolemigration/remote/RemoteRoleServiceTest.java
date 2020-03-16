package uk.gov.justice.digital.hmpps.keyworker.rolemigration.remote;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.junit.Rule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import uk.gov.justice.digital.hmpps.keyworker.rolemigration.RoleService;
import uk.gov.justice.digital.hmpps.keyworker.services.AbstractServiceTest;
import uk.gov.justice.digital.hmpps.keyworker.services.RestCallHelper;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

class RemoteRoleServiceTest extends AbstractServiceTest {

    private static final int TEST_PORT = 8080;
    private static final String USERNAME_1 = "UN1";
    private static final String USERNAME_2 = "UN2";
    private static final String USERNAME_3 = "UN3";

    private ObjectMapper objectMapper = new ObjectMapper();

    private RoleService service;

    @Rule
    public WireMockRule server = new WireMockRule(TEST_PORT);

    @BeforeEach
    void setUp() {
        final var webClient = WebClient.builder().baseUrl(format("http://localhost:%s", TEST_PORT)).build();
        server.start();
        service = new RemoteRoleService(new RestCallHelper(webClient, webClient));
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

   @Test
   void givenRoleService_whenAssignRoleToApiCaseloadInvoked_thenExpectedHttpExchangeOccurs() {
        server.stubFor(
                put(urlEqualTo("/users/UN1/access-role/RC"))
                        .willReturn(aResponse().withStatus(200))
        );

        service.assignRoleToApiCaseload(USERNAME_1, "RC");

        server.verify(putRequestedFor(urlEqualTo("/users/UN1/access-role/RC")));
    }

    @Test
    void givenRoleService_whenRemoveRoleInvoked_thenExpectedHttpExchangeOccurs() {
        server.stubFor(
                delete(urlEqualTo("/users/UN1/caseload/CL/access-role/RC"))
                .willReturn(aResponse().withStatus(200))
        );

        service.removeRole(USERNAME_1, "CL", "RC");

        server.verify(deleteRequestedFor(urlEqualTo("/users/UN1/caseload/CL/access-role/RC")));
    }

    @Test
    void givenRoleService_whenFindStaffMatchingCaseloadAndRoleInvoked_thenExpectedHttpExchangeOccursAndResultsAreCorrect() throws JsonProcessingException {
        server.stubFor(
                get(urlEqualTo("/users/access-roles/caseload/CL/access-role/RC"))
                .willReturn(aResponse().withStatus(200).withBody(usernames()).withHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE))
        );

        final var usernames = service.findUsersForPrisonHavingRole("CL", "RC");

        server.verify(getRequestedFor(urlEqualTo("/users/access-roles/caseload/CL/access-role/RC")));
        assertThat(usernames).containsExactlyInAnyOrder(USERNAME_1, USERNAME_2, USERNAME_3);
    }

    private String usernames() throws JsonProcessingException {
        return usernames(USERNAME_1, USERNAME_3, USERNAME_2);
    }

    private String usernames(final String... usernames) throws JsonProcessingException {
       return objectMapper.writeValueAsString(usernames);
    }
}
