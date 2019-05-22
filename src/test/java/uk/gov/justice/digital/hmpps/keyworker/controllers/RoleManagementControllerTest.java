package uk.gov.justice.digital.hmpps.keyworker.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import uk.gov.justice.digital.hmpps.keyworker.dto.RoleAssignmentsSpecification;
import uk.gov.justice.digital.hmpps.keyworker.rolemigration.RoleAssignmentsService;
import uk.gov.justice.digital.hmpps.keyworker.rolemigration.UserRolesMigrationService;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class RoleManagementControllerTest {

    private static final String PATH = "/caseloads-roles";

    private MockMvc mvc;

    private RoleAssignmentsService roleAssignmentsService;

    private static final ObjectMapper mapper = new ObjectMapper();

    private static final RoleAssignmentsSpecification specification = RoleAssignmentsSpecification
            .builder()
            .caseloads(List.of("MDI", "LEI"))
            .rolesToMatch(List.of("100", "200"))
            .rolesToAssign(List.of("P", "Q", "R"))
            .rolesToRemove(List.of("X", "Y"))
            .build();


    @Before
    public void configure() {
        roleAssignmentsService = mock(RoleAssignmentsService.class);
        mvc = MockMvcBuilders.standaloneSetup(new RoleMangementController(roleAssignmentsService)).build();
    }


    @Test
    public void testValidJson() throws Exception {
        mvc.perform(
                post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(specification))
        ).andExpect(status().isNoContent());

        verify(roleAssignmentsService).updateRoleAssignments(specification);
    }

    @Test
    public void testJsonNoContent() throws Exception {
        mvc.perform(
                post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
        ).andExpect(status().is4xxClientError());
    }

    @Test
    public void testJsonEmptyObject() throws Exception {
        mvc.perform(
                post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
        ).andExpect(status().isNoContent());
    }


    @Test
    public void testValidUrlEncodedForm() throws Exception {
        mvc.perform(
                post(PATH)
                        .param("caseloads", "MDI", "LEI")
                        .param("rolesToMatch", "100", "200")
                        .param("rolesToAssign", "P", "Q", "R")
                        .param("rolesToRemove", "X", "Y")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        ).andExpect(status().isNoContent());

        verify(roleAssignmentsService).updateRoleAssignments(specification);
    }
}
