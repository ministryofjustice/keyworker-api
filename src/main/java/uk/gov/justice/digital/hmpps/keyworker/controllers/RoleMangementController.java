package uk.gov.justice.digital.hmpps.keyworker.controllers;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import uk.gov.justice.digital.hmpps.keyworker.dto.RoleAssignmentsSpecification;
import uk.gov.justice.digital.hmpps.keyworker.rolemigration.UserRolesMigrationService;

@Api(tags = {"caseloads-roles"})

@RestController
@RequestMapping(value="caseloads-roles")
@Slf4j
public class RoleMangementController {

    private final UserRolesMigrationService userRolesMigrationService;

    RoleMangementController(UserRolesMigrationService userRolesMigrationService) {
        this.userRolesMigrationService = userRolesMigrationService;
    }

    @ApiOperation(
            value = "Assign roles according to the provided specification",
            nickname="assignRolesJson",
            consumes = MediaType.APPLICATION_JSON_VALUE
    )
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE )
    public ResponseEntity assignRolesJson(@RequestBody RoleAssignmentsSpecification specification) {
        userRolesMigrationService.updateRoleAssignments(specification);
        return ResponseEntity.noContent().build();
    }

    @ApiOperation(
            value = "Assign roles according to the provided specification",
            notes = "Form keys are the same as the object property names in the JSON version: 'caseloads', 'rolesToMatch', 'rolesToAssign', 'rolesToRemove'.",
            nickname="assignRolesFormUrlEncoded",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE
    )
    @PostMapping(consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE )
    public ResponseEntity assignRolesForm(@ApiParam(value = "An application/x-www-form-urlencoded form.  Keys 'caseloads' and 'rolesToMatch' are mandatory, 'rolesToAdd' and 'rolesToRemove' should be supplied as needed") @RequestParam MultiValueMap<String, String> form) {
        userRolesMigrationService.updateRoleAssignments(RoleAssignmentsSpecification.fromForm(form));
        return ResponseEntity.noContent().build();
    }


}
