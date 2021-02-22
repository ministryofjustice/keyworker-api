package uk.gov.justice.digital.hmpps.keyworker.controllers

import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import io.swagger.annotations.ApiResponse
import io.swagger.annotations.ApiResponses
import org.slf4j.LoggerFactory
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.keyworker.dto.ErrorResponse
import uk.gov.justice.digital.hmpps.keyworker.dto.KeyworkerStatSummary
import uk.gov.justice.digital.hmpps.keyworker.dto.KeyworkerStatsDto
import uk.gov.justice.digital.hmpps.keyworker.dto.Prison
import uk.gov.justice.digital.hmpps.keyworker.services.KeyworkerStatsService
import uk.gov.justice.digital.hmpps.keyworker.services.PrisonSupportedService
import java.time.LocalDate
import java.util.stream.Collectors

@Api(tags = ["key-worker-stats"])
@RestController
@RequestMapping(value = ["key-worker-stats"], produces = [MediaType.APPLICATION_JSON_VALUE])
class KeyworkerStatsController(
  private val keyworkerStatsService: KeyworkerStatsService,
  private val prisonSupportedService: PrisonSupportedService
) {
  @ApiOperation(
    value = "Return staff members stats",
    notes = "Statistic for key workers and the prisoners that they support",
    nickname = "getStatsForStaff"
  )
  @ApiResponses(
    value =
    [
      ApiResponse(code = 200, message = "OK", response = KeyworkerStatsDto::class),
      ApiResponse(code = 400, message = "Invalid request", response = ErrorResponse::class),
      ApiResponse(
        code = 500,
        message = "Unrecoverable error occurred whilst processing request.",
        response = ErrorResponse::class
      )
    ]
  )
  @GetMapping(path = ["/{staffId}/prison/{prisonId}"])
  fun getStatsForStaff(
    @ApiParam("staffId") @PathVariable("staffId") staffId: Long,
    @ApiParam("prisonId") @PathVariable("prisonId") prisonId: String,
    @ApiParam(value = "Calculate stats for staff on or after this date (in YYYY-MM-DD format).") @RequestParam(value = "fromDate") @DateTimeFormat(
      iso = DateTimeFormat.ISO.DATE
    ) fromDate: LocalDate?,
    @ApiParam(value = "Calculate stats for staff on or before this date (in YYYY-MM-DD format).") @RequestParam(value = "toDate") @DateTimeFormat(
      iso = DateTimeFormat.ISO.DATE
    ) toDate: LocalDate?
  ): KeyworkerStatsDto = keyworkerStatsService.getStatsForStaff(staffId, prisonId, fromDate, toDate)

  @ApiOperation(value = "Get Key Worker stats for any prison.", nickname = "getAllPrisonStats")
  @ApiResponses(
    value =
    [
      ApiResponse(code = 200, message = "OK", responseContainer = "Map", response = KeyworkerStatSummary::class),
      ApiResponse(code = 400, message = "Invalid request.", response = ErrorResponse::class),
      ApiResponse(code = 404, message = "Requested resource not found.", response = ErrorResponse::class),
      ApiResponse(
        code = 500,
        message = "Unrecoverable error occurred whilst processing request.",
        response = ErrorResponse::class
      )
    ]
  )
  @GetMapping
  fun getPrisonStats(
    @ApiParam(value = "List of prisonIds", allowMultiple = true, example = "prisonId=MDI&prisonId=LEI") @RequestParam(
      value = "prisonId"
    ) prisonIdList: List<String>?,
    @ApiParam(value = "Start Date of Stats, optional, will choose one month before toDate (in YYYY-MM-DD format)") @RequestParam(
      value = "fromDate"
    ) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) fromDate: LocalDate?,
    @ApiParam(value = "End Date of Stats (inclusive), optional, will choose yesterday if not provided (in YYYY-MM-DD format)") @RequestParam(
      value = "toDate"
    ) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) toDate: LocalDate?
  ): KeyworkerStatSummary {
    val prisonIds: MutableList<String> = ArrayList()
    if (prisonIdList == null || prisonIdList.isEmpty()) {
      val migratedPrisons = prisonSupportedService.migratedPrisons
      prisonIds.addAll(migratedPrisons.stream().map { obj: Prison -> obj.prisonId }.collect(Collectors.toList()))
    } else {
      prisonIds.addAll(prisonIdList)
    }
    log.debug("getting key-workers stats for prisons {}", prisonIds)
    return keyworkerStatsService.getPrisonStats(prisonIds, fromDate, toDate)
  }

  companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }
}
