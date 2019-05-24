package uk.gov.justice.digital.hmpps.keyworker.controllers;

import io.swagger.annotations.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import uk.gov.justice.digital.hmpps.keyworker.dto.RoleAssignmentsSpecification;
import uk.gov.justice.digital.hmpps.keyworker.rolemigration.RoleAssignmentsService;

@Api(tags = {"caseloads-roles"})

@RestController
@RequestMapping(value = "caseloads-roles")
@Slf4j
public class RoleManagementController {

    private final RoleAssignmentsService roleAssignmentsService;

    public RoleManagementController(RoleAssignmentsService roleAssignmentsService) {
        this.roleAssignmentsService = roleAssignmentsService;
    }

    @ApiOperation(
            value = "Assign roles according to the provided specification",
            nickname = "assignRolesJson",
            consumes = MediaType.APPLICATION_JSON_VALUE
    )
    @ApiResponses({
            @ApiResponse(code = 204, message = "Successful"),
            @ApiResponse(code = 400, message = "Client error")
    })
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity assignRolesJson(@RequestBody RoleAssignmentsSpecification specification) {
        roleAssignmentsService.updateRoleAssignments(specification);
        return ResponseEntity.noContent().build();
    }

    @ApiOperation(
            value = "Assign roles according to the provided specification",
            notes = "Form keys are the same as the object property names in the JSON version: 'caseloads', 'rolesToMatch', 'rolesToAssign', 'rolesToRemove'.",
            nickname = "assignRolesFormUrlEncoded",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE
    )
    @ApiResponses({
            @ApiResponse(code = 204, message = "Successful"),
            @ApiResponse(code = 400, message = "Client error")
    })
    @PostMapping(consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity assignRolesForm(@ApiParam(value = "An application/x-www-form-urlencoded form.  Keys 'caseloads' and 'rolesToMatch' are mandatory, 'rolesToAdd' and 'rolesToRemove' should be supplied as needed") @RequestParam MultiValueMap<String, String> form) {
        roleAssignmentsService.updateRoleAssignments(RoleAssignmentsSpecification.fromForm(form));
        return ResponseEntity.noContent().build();
    }
}
