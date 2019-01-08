package uk.gov.justice.digital.hmpps.keyworker.controllers;

import io.swagger.annotations.*;
import org.apache.camel.ProducerTemplate;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.justice.digital.hmpps.keyworker.batch.EnableNewNomisRoute;
import uk.gov.justice.digital.hmpps.keyworker.dto.ErrorResponse;

@Api(tags = {"admin"}, hidden = true)
@RestController
@RequestMapping(
        value="admin",
        produces = MediaType.APPLICATION_JSON_VALUE)
public class AdminController {

    private final ProducerTemplate producerTemplate;

    public AdminController(ProducerTemplate producerTemplate) {
        this.producerTemplate = producerTemplate;
    }


    @ApiOperation(
            value = "Enable Users access to New Nomis prison by prison batch process",
            nickname = "runEnableNewNomisBatch",
            authorizations = { @Authorization("SYSTEM_USER") },
            hidden = true)

    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "OK"),
            @ApiResponse(code = 500, message = "Unrecoverable error occurred whilst processing request.", response = ErrorResponse.class) })

    @PostMapping(path = "/batch/add-users-to-new-nomis")
//    @PreAuthorize("hasRole('SYSTEM_USER')")
    public void runEnableNewNomisBatch() {
        producerTemplate.send(EnableNewNomisRoute.ENABLE_NEW_NOMIS, exchange -> {});
    }

}
