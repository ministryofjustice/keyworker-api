package uk.gov.justice.digital.hmpps.keyworker.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.justice.digital.hmpps.keyworker.dto.ErrorResponse;
import uk.gov.justice.digital.hmpps.keyworker.services.KeyworkerBatchService;

import java.util.List;

@Tag(name = "batch")
@RestController
@RequestMapping(
        value="batch",
        produces = MediaType.APPLICATION_JSON_VALUE)
@Slf4j
public class BatchController {

    private final KeyworkerBatchService keyworkerBatchService;

    public BatchController(KeyworkerBatchService keyworkerBatchService) {
        this.keyworkerBatchService = keyworkerBatchService;
    }

    @Operation(
            description = "Checks for non active keyworkers with a reached active_date and updates the status to active, Can only be run with SYSTEM_USER role",
            hidden = true,
            security = { @SecurityRequirement(name = "SYSTEM_USER") },
            summary="runBatchUpdateStatus")

    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Update status process complete", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))}),
            @ApiResponse(responseCode = "500", description = "Unrecoverable error occurred whilst processing request.", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))}) })

    @PostMapping(path = "/update-status")
    public List<Long> runBatchUpdateStatus() {
        final List<Long> ids = keyworkerBatchService.executeUpdateStatus();
        log.info("processed /batch/updateStatus call. The following key workers have been set to status active: {}", ids.size());
        log.info("Keyworkers updated to active status: {}", ids.size());
        return ids;
    }
}
