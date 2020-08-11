package uk.gov.justice.digital.hmpps.keyworker.controllers;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
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

@Api(tags = {"caseloads-roles"})

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

    @ApiOperation(
            value = "Assign roles according to the provided specification",
            nickname = "assignRolesJson",
            notes = "This returns a map of Prison ID to results of the role assignments",
            consumes = MediaType.APPLICATION_JSON_VALUE
    )
    @ApiResponses({
            @ApiResponse(code = 200, message = "Ok", response = RoleAssignmentStats.class, responseContainer = "List"),
            @ApiResponse(code = 400, message = "Client error")
    })
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<RoleAssignmentStats>> assignRolesJson(@RequestBody final RoleAssignmentsSpecification specification) {
        final var result = roleAssignmentsService.updateRoleAssignments(specification);
        return ResponseEntity.ok(result);
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
    public ResponseEntity assignRolesForm(@ApiParam(value = "An application/x-www-form-urlencoded form.  Keys 'caseloads' and 'rolesToMatch' are mandatory, 'rolesToAdd' and 'rolesToRemove' should be supplied as needed") @RequestParam final MultiValueMap<String, String> form) {
        roleAssignmentsService.updateRoleAssignments(RoleAssignmentsSpecification.fromForm(form));
        return ResponseEntity.noContent().build();
    }
}
