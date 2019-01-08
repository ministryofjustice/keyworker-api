package uk.gov.justice.digital.hmpps.keyworker.controllers;

import io.swagger.annotations.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import uk.gov.justice.digital.hmpps.keyworker.dto.ErrorResponse;
import uk.gov.justice.digital.hmpps.keyworker.dto.KeyworkerStatSummary;
import uk.gov.justice.digital.hmpps.keyworker.dto.KeyworkerStatsDto;
import uk.gov.justice.digital.hmpps.keyworker.dto.Prison;
import uk.gov.justice.digital.hmpps.keyworker.model.PrisonKeyWorkerStatistic;
import uk.gov.justice.digital.hmpps.keyworker.services.KeyworkerStatsService;
import uk.gov.justice.digital.hmpps.keyworker.services.PrisonSupportedService;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Api(tags = {"key-worker-stats"})

@RestController
@RequestMapping(
        value="key-worker-stats",
        produces = MediaType.APPLICATION_JSON_VALUE)
@Slf4j
public class KeyworkerStatsController {
    private final KeyworkerStatsService keyworkerStatsService;
    private final PrisonSupportedService prisonSupportedService;

    public KeyworkerStatsController(KeyworkerStatsService keyworkerStatsService, PrisonSupportedService prisonSupportedService) {
        this.keyworkerStatsService = keyworkerStatsService;
        this.prisonSupportedService = prisonSupportedService;
    }

    @ApiOperation(
            value = "Return staff members stats",
            notes = "Statistic for key workers and the prisoners that they support",
            nickname="getStatsForStaff")

    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "OK", response = KeyworkerStatsDto.class),
            @ApiResponse(code = 400, message = "Invalid request", response = ErrorResponse.class),
            @ApiResponse(code = 500, message = "Unrecoverable error occurred whilst processing request.", response = ErrorResponse.class) })

    @GetMapping(path = "/{staffId}/prison/{prisonId}")
    public KeyworkerStatsDto getStatsForStaff(
            @ApiParam("staffId") @NotNull @PathVariable("staffId")
                    Long staffId,

            @ApiParam("prisonId") @NotNull @PathVariable("prisonId")
                    String prisonId,

            @ApiParam(value = "Calculate stats for staff on or after this date (in YYYY-MM-DD format).")
            @RequestParam(value = "fromDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate fromDate,

            @ApiParam(value = "Calculate stats for staff on or before this date (in YYYY-MM-DD format).")
            @RequestParam(value = "toDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate toDate )
    {

        return keyworkerStatsService.getStatsForStaff(staffId, prisonId, fromDate, toDate);

    }

    /* --------------------------------------------------------------------------------*/

    @ApiOperation(
            value = "Get Key Worker stats for any prison.",
            nickname="getAllPrisonStats")

    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "OK", responseContainer = "Map", response = KeyworkerStatSummary.class),
            @ApiResponse(code = 400, message = "Invalid request.", response = ErrorResponse.class ),
            @ApiResponse(code = 404, message = "Requested resource not found.", response = ErrorResponse.class),
            @ApiResponse(code = 500, message = "Unrecoverable error occurred whilst processing request.", response = ErrorResponse.class) })

    @GetMapping
    public KeyworkerStatSummary getPrisonStats(
            @ApiParam(value = "List of prisonIds", allowMultiple = true, example = "prisonId=MDI&prisonId=LEI")
            @RequestParam(value = "prisonId", required = false)
                    List<String> prisonIdList,
            @ApiParam(value = "Start Date of Stats, optional, will chosse one month before toDate (in YYYY-MM-DD format)")
            @RequestParam(value = "fromDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate fromDate,
            @ApiParam(value = "End Date of Stats, optional, will chosse yesterday if not provided (in YYYY-MM-DD format)")
            @RequestParam(value = "toDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate toDate) {

        final List<String> prisonIds = new ArrayList<>();
        if (prisonIdList == null || prisonIdList.isEmpty()) {
            List<Prison> migratedPrisons = prisonSupportedService.getMigratedPrisons();
            prisonIds.addAll(migratedPrisons.stream().map(Prison::getPrisonId).collect(Collectors.toList()));
        } else {
            prisonIds.addAll(prisonIdList);
        }
        log.debug("getting key-workers stats for prisons {}", prisonIds);
        return keyworkerStatsService.getPrisonStats(prisonIds, fromDate, toDate);
    }

    @ApiOperation(
            value = "Generate prison stats at specified prison.",
            notes = "Requires KW Migration Privilege",
            nickname = "runBatchPrisonStats",
            authorizations = { @Authorization("KW_MIGRATION") },
            hidden = true)

    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "OK", response = PrisonKeyWorkerStatistic.class),
            @ApiResponse(code = 400, message = "Invalid request.", response = ErrorResponse.class ),
            @ApiResponse(code = 404, message = "Requested resource not found.", response = ErrorResponse.class),
            @ApiResponse(code = 500, message = "Unrecoverable error occurred whilst processing request.", response = ErrorResponse.class) })

    @PostMapping(path = "/batch/{prisonId}")
    @PreAuthorize("hasRole('KW_MIGRATION')")
    public PrisonKeyWorkerStatistic runBatchPrisonStats(
            @ApiParam(value = "prisonId", required = true)
            @NotEmpty
            @PathVariable(name = "prisonId") String prisonId) {
        return keyworkerStatsService.generatePrisonStats(prisonId);
    }

}
