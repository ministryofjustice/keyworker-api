package uk.gov.justice.digital.hmpps.keyworker.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.justice.digital.hmpps.keyworker.dto.RoleAssignmentStats;
import uk.gov.justice.digital.hmpps.keyworker.dto.RoleAssignmentsSpecification;
import uk.gov.justice.digital.hmpps.keyworker.rolemigration.RoleAssignmentsService;

import java.util.List;

@Tag(name = "caseloads-roles")

@RestController
@RequestMapping(
        value = "caseloads-roles",
        produces = MediaType.APPLICATION_JSON_VALUE)
@Slf4j
public class RoleManagementController {

    private final RoleAssignmentsService roleAssignmentsService;

    public RoleManagementController(final RoleAssignmentsService roleAssignmentsService) {
        this.roleAssignmentsService = roleAssignmentsService;
    }

    @Operation(
            description = "Assign roles according to the provided specification",
            summary = "assignRolesJson"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "400", description = "Client error")
    })
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<RoleAssignmentStats>> assignRolesJson(@RequestBody final RoleAssignmentsSpecification specification) {
        final var result = roleAssignmentsService.updateRoleAssignments(specification);
        return ResponseEntity.ok(result);
    }

    @Operation(
            description = "Form keys are the same as the object property names in the JSON version: 'caseloads', 'rolesToMatch', 'rolesToAssign', 'rolesToRemove'.",
            summary = "assignRolesFormUrlEncoded"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Successful"),
            @ApiResponse(responseCode = "400", description = "Client error")
    })
    @PostMapping(consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity assignRolesForm(@Parameter(description = "An application/x-www-form-urlencoded form.  Keys 'caseloads' and 'rolesToMatch' are mandatory, 'rolesToAdd' and 'rolesToRemove' should be supplied as needed") @RequestParam final MultiValueMap<String, String> form) {
        roleAssignmentsService.updateRoleAssignments(RoleAssignmentsSpecification.fromForm(form));
        return ResponseEntity.noContent().build();
    }
}
