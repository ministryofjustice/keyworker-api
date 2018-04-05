package uk.gov.justice.digital.hmpps.keyworker.rolemigration.remote;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import uk.gov.justice.digital.hmpps.keyworker.rolemigration.RoleService;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

public class RemoteRoleServiceTest {

    private MockRestServiceServer server;

    private ObjectMapper objectMapper;

    private RoleService service;

    @Before
    public void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();

        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        service = new RemoteRoleService(restTemplate);
    }

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

        Set<Long> staffIds = service.findStaffForPrisonHavingRole("CL","RC");

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
