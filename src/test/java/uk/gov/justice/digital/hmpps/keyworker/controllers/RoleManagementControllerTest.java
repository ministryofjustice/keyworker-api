package uk.gov.justice.digital.hmpps.keyworker.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import uk.gov.justice.digital.hmpps.keyworker.dto.RoleAssignmentStats;
import uk.gov.justice.digital.hmpps.keyworker.dto.RoleAssignmentsSpecification;
import uk.gov.justice.digital.hmpps.keyworker.rolemigration.RoleAssignmentsService;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
        mvc = MockMvcBuilders.standaloneSetup(new RoleManagementController(roleAssignmentsService)).build();
    }


    @Test
    public void testValidJson() throws Exception {

        when(roleAssignmentsService.updateRoleAssignments(any())).thenReturn(Map.of("MDI", RoleAssignmentStats.builder()
                .numAssignRoleSucceeded(1L)
                .numAssignRoleFailed(2L)
                .numUnassignRoleSucceeded(3L)
                .numUnassignRoleIgnored(4L)
                .numUnassignRoleFailed(5L)
                .build()));

        mvc.perform(
                post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(specification))
        ).andExpect(status().isOk())
                .andExpect(jsonPath("$.MDI.numAssignRoleSucceeded").value(1))
                .andExpect(jsonPath("$.MDI.numAssignRoleFailed").value(2))
                .andExpect(jsonPath("$.MDI.numUnassignRoleSucceeded").value(3))
                .andExpect(jsonPath("$.MDI.numUnassignRoleIgnored").value(4))
                .andExpect(jsonPath("$.MDI.numUnassignRoleFailed").value(5));


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
        ).andExpect(status().isOk());
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
