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

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RoleService service;

    @Autowired
    private MockRestServiceServer server;

   @Test
    public void givenRoleService_whenAssignRoleToApiCaseloadInvoked_thenExpectedHttpExchangeOccurs() throws JsonProcessingException {
        server
                .expect(requestTo("/staff/1/access-roles/"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().string("RC"))
                .andRespond(withSuccess(staffUserRole(), MediaType.APPLICATION_JSON));

        service.assignRoleToApiCaseload(1L, "RC");

        server.verify();
    }

    @Test
    public void givenRoleService_whenRemoveRoleInvoked_thenExpectedHttpExchangeOccurs() {
        server
                .expect(requestTo("/staff/1/access-roles/caseload/CL/access-role/RC"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withSuccess("string", MediaType.APPLICATION_JSON));

        service.removeRole(1L, "CL", "RC");

        server.verify();
    }


    @Test
    public void givenRoleService_whenFindStaffMatchingCaseloadAndRoleInvoked_thenExpectedHttpExchangeOccursAndResultsAreCorrect() throws JsonProcessingException {
        server
                .expect(requestTo("/staff/access-roles/caseload/CL/access-role/RC"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(staffUserRoles(), MediaType.APPLICATION_JSON));

        Set<Long> staffIds = service.findStaffForPrisonHavingRole("CL", "RC");

        server.verify();

        assertThat(staffIds).containsExactlyInAnyOrder(1L, 2L, 3L);
    }

    private String staffUserRole() throws JsonProcessingException {
        return toJson(StaffUserRoleDto
                .builder()
                .caseloadId("CLID")
                .parentRoleCode("PRC")
                .roleCode("RC")
                .roleName("ROLENAME")
                .username("USERNAME")
                .roleId(-1L)
                .staffId(1L)
                .build());
    }

    private String staffUserRoles() throws JsonProcessingException {
        StaffUserRoleDto.StaffUserRoleDtoBuilder builder = StaffUserRoleDto
                .builder()
                .caseloadId("CLID")
                .parentRoleCode("PRC")
                .roleCode("RC")
                .roleName("ROLENAME")
                .username("USERNAME")
                .roleId(-1L);

        return toJson(
                builder.staffId(1L).build(),
                builder.staffId(2L).build(),
                builder.staffId(2L).build(),
                builder.staffId(3L).build()
        );
    }

    private String toJson(StaffUserRoleDto staffUserRole) throws JsonProcessingException {
        return objectMapper.writeValueAsString(staffUserRole);
    }

    private String toJson(StaffUserRoleDto... staffUserRoles) throws JsonProcessingException {
        return objectMapper.writeValueAsString(staffUserRoles);
    }
}
