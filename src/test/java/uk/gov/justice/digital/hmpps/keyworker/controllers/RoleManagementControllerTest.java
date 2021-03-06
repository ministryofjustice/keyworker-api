package uk.gov.justice.digital.hmpps.keyworker.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import uk.gov.justice.digital.hmpps.keyworker.dto.RoleAssignmentStats;
import uk.gov.justice.digital.hmpps.keyworker.dto.RoleAssignmentsSpecification;
import uk.gov.justice.digital.hmpps.keyworker.rolemigration.RoleAssignmentsService;

import java.util.List;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RoleManagementControllerTest {

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


    @BeforeEach
    void configure() {
        roleAssignmentsService = mock(RoleAssignmentsService.class);
        mvc = MockMvcBuilders.standaloneSetup(new RoleManagementController(roleAssignmentsService)).build();
    }


    @Test
    void testValidJson() throws Exception {

        when(roleAssignmentsService.updateRoleAssignments(any())).thenReturn(List.of(RoleAssignmentStats.builder()
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
                .andExpect(jsonPath("$[0].numAssignRoleSucceeded").value(1))
                .andExpect(jsonPath("$[0].numAssignRoleFailed").value(2))
                .andExpect(jsonPath("$[0].numUnassignRoleSucceeded").value(3))
                .andExpect(jsonPath("$[0].numUnassignRoleIgnored").value(4))
                .andExpect(jsonPath("$[0].numUnassignRoleFailed").value(5));


        verify(roleAssignmentsService).updateRoleAssignments(specification);
    }

    @Test
    void testJsonNoContent() throws Exception {
        mvc.perform(
                post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
        ).andExpect(status().is4xxClientError());
    }

    @Test
    void testJsonEmptyObject() throws Exception {
        mvc.perform(
                post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
        ).andExpect(status().isOk());
    }


    @Test
    void testValidUrlEncodedForm() throws Exception {
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
