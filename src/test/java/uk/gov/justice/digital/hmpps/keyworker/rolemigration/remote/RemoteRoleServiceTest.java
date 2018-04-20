package uk.gov.justice.digital.hmpps.keyworker.rolemigration.remote;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.client.AutoConfigureWebClient;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.client.MockRestServiceServer;
import uk.gov.justice.digital.hmpps.keyworker.rolemigration.RoleService;
import uk.gov.justice.digital.hmpps.keyworker.services.AbstractServiceTest;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@RunWith(SpringRunner.class)
@RestClientTest(RemoteRoleService.class)
@AutoConfigureWebClient(registerRestTemplate=true)

public class RemoteRoleServiceTest extends AbstractServiceTest {

    private static final String USERNAME_1 = "UN1";
    private static final String USERNAME_2 = "UN2";
    private static final String USERNAME_3 = "UN3";

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RoleService service;

    @Autowired
    private MockRestServiceServer server;

   @Test
    public void givenRoleService_whenAssignRoleToApiCaseloadInvoked_thenExpectedHttpExchangeOccurs() throws JsonProcessingException {
        server
                .expect(requestTo("/users/UN1/access-roles/RC"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(content().bytes(new byte[0]))
                .andRespond(withSuccess());

        service.assignRoleToApiCaseload(USERNAME_1, "RC");

        server.verify();
    }

    @Test
    public void givenRoleService_whenRemoveRoleInvoked_thenExpectedHttpExchangeOccurs() {
        server
                .expect(requestTo("/users/UN1/caseload/CL/access-role/RC"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withSuccess());

        service.removeRole(USERNAME_1, "CL", "RC");

        server.verify();
    }

    @Test
    public void givenRoleService_whenFindStaffMatchingCaseloadAndRoleInvoked_thenExpectedHttpExchangeOccursAndResultsAreCorrect() throws JsonProcessingException {
        server
                .expect(requestTo("/users/access-roles/caseload/CL/access-role/RC"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(usernames(), MediaType.APPLICATION_JSON));

        Set<String> usernames = service.findUsersForPrisonHavingRole("CL", "RC");

        server.verify();

        assertThat(usernames).containsExactlyInAnyOrder(USERNAME_1, USERNAME_2, USERNAME_3);
    }

    private String usernames() throws JsonProcessingException {
        return usernames(USERNAME_1, USERNAME_3, USERNAME_2);
    }

    private String usernames(String... usernames) throws JsonProcessingException {
       return objectMapper.writeValueAsString(usernames);
    }
}
