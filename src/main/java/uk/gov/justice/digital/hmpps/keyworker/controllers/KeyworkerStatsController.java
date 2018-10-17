package uk.gov.justice.digital.hmpps.keyworker.controllers;

import io.swagger.annotations.*;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.constraints.NotEmpty;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import uk.gov.justice.digital.hmpps.keyworker.dto.ErrorResponse;
import uk.gov.justice.digital.hmpps.keyworker.dto.KeyworkerStatsDto;
import uk.gov.justice.digital.hmpps.keyworker.dto.PrisonStatsDto;
import uk.gov.justice.digital.hmpps.keyworker.services.KeyworkerStatsService;

import javax.validation.constraints.NotNull;
import java.time.LocalDate;

@Api(tags = {"key-worker-stats"})

@RestController
@RequestMapping(
        value="key-worker-stats",
        produces = MediaType.APPLICATION_JSON_VALUE)
@Slf4j
public class KeyworkerStatsController {
    private final KeyworkerStatsService keyworkerStatsService;

    public KeyworkerStatsController(KeyworkerStatsService keyworkerStatsService) {
        this.keyworkerStatsService = keyworkerStatsService;
    }

    @ApiOperation(
            value = "Return staff members stats",
            notes = "Can only be run with the key worker role",
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
            @RequestParam(value = "fromDate")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate fromDate,

            @ApiParam(value = "Calculate stats for staff on or before this date (in YYYY-MM-DD format).")
            @RequestParam(value = "toDate")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate toDate )
    {

        return keyworkerStatsService.getStatsForStaff(staffId, prisonId, fromDate, toDate);

    }

    /* --------------------------------------------------------------------------------*/

    @ApiOperation(
            value = "Get Keyworker stats at specified prison.",
            notes = "Get Keyworker stats at specified prison.",
            nickname="getKeyworkerStats")

    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "OK", response = PrisonStatsDto.class),
            @ApiResponse(code = 400, message = "Invalid request.", response = ErrorResponse.class ),
            @ApiResponse(code = 404, message = "Requested resource not found.", response = ErrorResponse.class),
            @ApiResponse(code = 500, message = "Unrecoverable error occurred whilst processing request.", response = ErrorResponse.class) })

    @GetMapping(path = "/{prisonId}")
    public PrisonStatsDto getKeyworkerStats(
            @ApiParam(value = "prisonId", required = true)
            @NotEmpty
            @PathVariable(name = "prisonId")
                    String prisonId,
            @ApiParam(value = "Start Date of Stats, optional, will chose previous start of last Month")
            @RequestParam(value = "startDate", required = false)
                    LocalDate startDate,
            @ApiParam(value = "End Date of Stats, optional, will chose previous end of last Month")
            @RequestParam(value = "endDate", required = false)
                    LocalDate endDate) {

        log.debug("getting key-workers stats for prison Id {}", prisonId);

        return keyworkerStatsService.getPrisonStats(prisonId, startDate, endDate);
    }
}
