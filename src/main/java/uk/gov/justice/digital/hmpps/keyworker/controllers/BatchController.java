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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.justice.digital.hmpps.keyworker.dto.ErrorResponse;
import uk.gov.justice.digital.hmpps.keyworker.services.KeyworkerBatchService;
import uk.gov.justice.digital.hmpps.keyworker.services.KeyworkerStatsBatchService;
import uk.gov.justice.digital.hmpps.keyworker.services.NomisBatchService;
import uk.gov.justice.digital.hmpps.keyworker.services.ReconciliationBatchService;

import java.util.List;

@Tag(name = "batch")
@RestController
@RequestMapping(
        value="batch",
        produces = MediaType.APPLICATION_JSON_VALUE)
@Slf4j
public class BatchController {

    private final KeyworkerBatchService keyworkerBatchService;

    private final KeyworkerStatsBatchService keyworkerStatsBatchService;

    private final NomisBatchService nomisBatchService;

    private final ReconciliationBatchService reconciliationBatchService;
    public BatchController(KeyworkerBatchService keyworkerBatchService, KeyworkerStatsBatchService keyworkerStatsBatchService, NomisBatchService nomisBatchService, ReconciliationBatchService reconciliationBatchService) {
        this.keyworkerBatchService = keyworkerBatchService;
        this.keyworkerStatsBatchService = keyworkerStatsBatchService;
        this.nomisBatchService = nomisBatchService;
        this.reconciliationBatchService = reconciliationBatchService;
    }

    @Operation(
        description = "Enable Users access to New Nomis prison by prison batch process, Can only be run with SYSTEM_USER role",
        summary = "runEnableNewNomisBatch",
        security = { @SecurityRequirement( name = "SYSTEM_USER") },
            hidden = true)

    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "500", description = "Unrecoverable error occurred whilst processing request.",
                content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))}) })

    @PostMapping(path = "/add-users-to-new-nomis")
    @PreAuthorize("hasRole('SYSTEM_USER')")
    public void runEnableNewNomisBatch() {
        nomisBatchService.enableNomis();
    }

    @Operation(
            description = "Generate prison stats at specified prison. Can only be run with SYSTEM_USER role",
            summary = "runBatchPrisonStats",
            security = { @SecurityRequirement(name = "SYSTEM_USER") },
            hidden = true)

    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "400", description = "Invalid request.", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))} ),
            @ApiResponse(responseCode = "404", description = "Requested resource not found.", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))}),
            @ApiResponse(responseCode = "500", description = "Unrecoverable error occurred whilst processing request.", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))}) })

    @PostMapping(path = "/generate-stats")
    @PreAuthorize("hasRole('SYSTEM_USER')")
    public void runBatchPrisonStats() {
        keyworkerStatsBatchService.generatePrisonStats();
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
    @PreAuthorize("hasRole('SYSTEM_USER')")
    public List<Long> runBatchUpdateStatus() {
        final List<Long> ids = keyworkerBatchService.executeUpdateStatus();
        log.info("processed /batch/updateStatus call. The following key workers have been set to status active: {}", ids.size());
        return ids;
    }

    /* --------------------------------------------------------------------------------*/

    @Operation(
            description = "Run the KW reconciliation process, Can only be run with SYSTEM_USER role",
            summary = "runKWReconciliation",
            security = { @SecurityRequirement(name = "SYSTEM_USER") },
            hidden = true)

    @ApiResponses(value = {
            @ApiResponse(responseCode ="200", description = "OK"),
            @ApiResponse(responseCode ="400", description = "Invalid request.", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))} ),
            @ApiResponse(responseCode ="404", description = "Requested resource not found.", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))}),
            @ApiResponse(responseCode ="500", description = "Unrecoverable error occurred whilst processing request.", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))}) })

    @PostMapping(path = "/key-worker-recon")
    @PreAuthorize("hasRole('SYSTEM_USER')")
    public void runKWReconciliation() {
        reconciliationBatchService.reconcileKeyWorkerAllocations();
    }

}
