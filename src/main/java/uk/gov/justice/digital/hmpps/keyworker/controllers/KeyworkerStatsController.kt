package uk.gov.justice.digital.hmpps.keyworker.controllers

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
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

@Tag(name = "key-worker-stats")
@RestController
@RequestMapping(value = ["key-worker-stats"], produces = [MediaType.APPLICATION_JSON_VALUE])
class KeyworkerStatsController(
  private val keyworkerStatsService: KeyworkerStatsService,
  private val prisonSupportedService: PrisonSupportedService,
) {
  @Operation(
    description = "Statistic for key workers and the prisoners that they support",
    summary = "getStatsForStaff",
  )
  @ApiResponses(
    value =
      [
        ApiResponse(
          responseCode = "200",
          description = "OK",
        ),
        ApiResponse(
          responseCode = "400",
          description = "Invalid request",
          content =
            [
              Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
              ),
            ],
        ),
        ApiResponse(
          responseCode = "500",
          description = "Unrecoverable error occurred whilst processing request.",
          content =
            [
              Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
              ),
            ],
        ),
      ],
  )
  @GetMapping(path = ["/{staffId}/prison/{prisonId}"])
  fun getStatsForStaff(
    @Parameter(name = "staffId")
    @PathVariable("staffId")
    staffId: Long,
    @Parameter(name = "prisonId")
    @PathVariable("prisonId")
    prisonId: String,
    @Parameter(description = "Calculate stats for staff on or after this date (in YYYY-MM-DD format).")
    @RequestParam(value = "fromDate")
    @DateTimeFormat(
      iso = DateTimeFormat.ISO.DATE,
    )
    fromDate: LocalDate?,
    @Parameter(description = "Calculate stats for staff on or before this date (in YYYY-MM-DD format).")
    @RequestParam(value = "toDate")
    @DateTimeFormat(
      iso = DateTimeFormat.ISO.DATE,
    )
    toDate: LocalDate?,
  ): KeyworkerStatsDto = keyworkerStatsService.getStatsForStaff(staffId, prisonId, fromDate, toDate)

  @Operation(description = "Get Key Worker stats for any prison.", summary = "getAllPrisonStats")
  @ApiResponses(
    value =
      [
        ApiResponse(
          responseCode = "200",
          description = "OK",
        ),
        ApiResponse(
          responseCode = "400",
          description = "Invalid request.",
          content =
            [
              Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
              ),
            ],
        ),
        ApiResponse(
          responseCode = "404",
          description = "Requested resource not found.",
          content =
            [
              Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
              ),
            ],
        ),
        ApiResponse(
          responseCode = "500",
          description = "Unrecoverable error occurred whilst processing request.",
          content =
            [
              Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
              ),
            ],
        ),
      ],
  )
  @GetMapping
  fun getPrisonStats(
    @Parameter(description = "List of prisonIds", example = "prisonId=MDI&prisonId=LEI")
    @RequestParam(
      value = "prisonId",
    )
    prisonIdList: List<String>?,
    @Parameter(description = "Start Date of Stats, optional, will choose one month before toDate (in YYYY-MM-DD format)")
    @RequestParam(
      value = "fromDate",
    )
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    fromDate: LocalDate?,
    @Parameter(description = "End Date of Stats (inclusive), optional, will choose yesterday if not provided (in YYYY-MM-DD format)")
    @RequestParam(
      value = "toDate",
    )
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    toDate: LocalDate?,
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
