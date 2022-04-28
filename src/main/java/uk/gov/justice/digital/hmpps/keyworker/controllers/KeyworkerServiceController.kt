package uk.gov.justice.digital.hmpps.keyworker.controllers

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.apache.commons.lang3.Validate
import org.slf4j.LoggerFactory
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.keyworker.dto.AllocationsFilterDto
import uk.gov.justice.digital.hmpps.keyworker.dto.BasicKeyworkerDto
import uk.gov.justice.digital.hmpps.keyworker.dto.ErrorResponse
import uk.gov.justice.digital.hmpps.keyworker.dto.KeyworkerAllocationDetailsDto
import uk.gov.justice.digital.hmpps.keyworker.dto.KeyworkerAllocationDto
import uk.gov.justice.digital.hmpps.keyworker.dto.KeyworkerDto
import uk.gov.justice.digital.hmpps.keyworker.dto.KeyworkerUpdateDto
import uk.gov.justice.digital.hmpps.keyworker.dto.OffenderKeyWorkerHistory
import uk.gov.justice.digital.hmpps.keyworker.dto.OffenderKeyWorkerHistorySummary
import uk.gov.justice.digital.hmpps.keyworker.dto.OffenderKeyworkerDto
import uk.gov.justice.digital.hmpps.keyworker.dto.OffenderLocationDto
import uk.gov.justice.digital.hmpps.keyworker.dto.PagingAndSortingDto
import uk.gov.justice.digital.hmpps.keyworker.dto.Prison
import uk.gov.justice.digital.hmpps.keyworker.dto.SortOrder
import uk.gov.justice.digital.hmpps.keyworker.model.AllocationType
import uk.gov.justice.digital.hmpps.keyworker.model.KeyworkerStatus
import uk.gov.justice.digital.hmpps.keyworker.rolemigration.UserRolesMigrationService
import uk.gov.justice.digital.hmpps.keyworker.services.KeyworkerAutoAllocationService
import uk.gov.justice.digital.hmpps.keyworker.services.KeyworkerMigrationService
import uk.gov.justice.digital.hmpps.keyworker.services.KeyworkerService
import uk.gov.justice.digital.hmpps.keyworker.services.PrisonSupportedService
import java.time.LocalDate
import java.util.Optional
import javax.persistence.EntityNotFoundException
import javax.validation.Valid

@Tag(name = "key-worker")
@RestController
@RequestMapping(value = ["key-worker"], produces = [MediaType.APPLICATION_JSON_VALUE])
class KeyworkerServiceController(
  private val keyworkerService: KeyworkerService,
  private val keyworkerMigrationService: KeyworkerMigrationService,
  private val keyworkerAutoAllocationService: KeyworkerAutoAllocationService,
  private val roleMigrationService: UserRolesMigrationService,
  private val prisonSupportedService: PrisonSupportedService
) {

  companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }

  @Operation(
    description = "Key workers available for allocation at specified prison.",
    summary = "getAvailableKeyworkers"
  )
  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "200",
        description = "OK",
      ), ApiResponse(
        responseCode = "400",
        description = "Invalid request.",
        content =
        [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = ErrorResponse::class)
          )
        ],
      ), ApiResponse(
        responseCode = "404",
        description = "Requested resource not found.",
        content =
        [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = ErrorResponse::class)
          )
        ],
      ), ApiResponse(
        responseCode = "500",
        description = "Unrecoverable error occurred whilst processing request.",
        content =
        [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = ErrorResponse::class)
          )
        ],
      )
    ]
  )
  @GetMapping(path = ["/{prisonId}/available"])
  fun getAvailableKeyworkers(
    @Parameter(name = "prisonId", required = true) @PathVariable(name = "prisonId") prisonId: String
  ): List<KeyworkerDto> {
    log.debug("finding available key-workers for prison Id {}", prisonId)
    return keyworkerService.getAvailableKeyworkers(prisonId, true)
  }

  @Operation(
    description = "Allocations in specified prison.",
    summary = "getAllocations"
  )
  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "200",
        description = "OK",
      ), ApiResponse(
        responseCode = "400",
        description = "Invalid request.",
        content =
        [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = ErrorResponse::class)
          )
        ],
      ), ApiResponse(
        responseCode = "404",
        description = "Requested resource not found.",
        content =
        [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = ErrorResponse::class)
          )
        ],
      ), ApiResponse(
        responseCode = "500",
        description = "Unrecoverable error occurred whilst processing request.",
        content =
        [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = ErrorResponse::class)
          )
        ],
      )
    ]
  )
  @GetMapping(path = ["/{prisonId}/allocations"])
  fun getKeyworkerAllocations(
    @Parameter(name = "prisonId", required = true) @PathVariable("prisonId") prisonId: String,
    @Parameter(description = "Optional filter by type of allocation. A for auto allocations, M for manual allocations.") @RequestParam(
      value = "allocationType",
      required = false
    ) allocationType: String?,
    @Parameter(description = "Returned allocations must have been assigned on or after this date (in YYYY-MM-DD format).") @RequestParam(
      value = "fromDate",
      required = false
    ) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) fromDate: LocalDate?,
    @Parameter(
      description = "Returned allocations must have been assigned on or before this date (in YYYY-MM-DD format).",
      example = "today's date"
    ) @RequestParam(
      value = "toDate",
      required = false
    ) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) toDate: LocalDate?,
    @Parameter(
      description = "Requested offset of first record in returned collection of allocation records.",
      example = "0"
    ) @RequestHeader(value = PagingAndSortingDto.HEADER_PAGE_OFFSET, defaultValue = "0") pageOffset: Long,
    @Parameter(description = "Requested limit to number of allocation records returned.", example = "10") @RequestHeader(
      value = PagingAndSortingDto.HEADER_PAGE_LIMIT,
      defaultValue = "10"
    ) pageLimit: Long,
    @Parameter(description = "Comma separated list of one or more of the following fields - <b>firstName, lastName, assigned</b>") @RequestHeader(
      value = PagingAndSortingDto.HEADER_SORT_FIELDS,
      defaultValue = ""
    ) sortFields: String,
    @Parameter(
      description = "Sort order (ASC or DESC) - defaults to ASC.",
      example = "ASC"
    ) @RequestHeader(value = PagingAndSortingDto.HEADER_SORT_ORDER, defaultValue = "ASC") sortOrder: SortOrder
  ): ResponseEntity<List<KeyworkerAllocationDetailsDto>> {
    val page = keyworkerService.getAllocations(
      AllocationsFilterDto
        .builder()
        .prisonId(prisonId)
        .allocationType(Optional.ofNullable(AllocationType.get(allocationType)))
        .fromDate(Optional.ofNullable(fromDate))
        .toDate(toDate ?: LocalDate.now())
        .build(),
      PagingAndSortingDto
        .builder()
        .pageOffset(pageOffset)
        .pageLimit(pageLimit)
        .sortFields(sortFields)
        .sortOrder(sortOrder)
        .build()
    )
    return ResponseEntity(page.items, page.toHeaders(), HttpStatus.OK)
  }

  @Operation(
    description = "Keyworker details for specified offenders in the given prison, where the offender and details exist.",
    summary = "getOffenderForPrison"
  )
  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "200",
        description = "OK",
      ), ApiResponse(
        responseCode = "400", description = "Invalid request.",
        content =
        [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = ErrorResponse::class)
          )
        ],
      ), ApiResponse(
        responseCode = "404",
        description = "Requested resource not found.",
        content =
        [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = ErrorResponse::class)
          )
        ],
      ), ApiResponse(
        responseCode = "500",
        description = "Unrecoverable error occurred whilst processing request.",
        content =
        [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = ErrorResponse::class)
          )
        ],
      )
    ]
  )
  @GetMapping(path = ["/{prisonId}/offenders"])
  fun getOffenderKeyworkerDetailsList(
    @Parameter(name = "prisonId", required = true) @PathVariable("prisonId") prisonId: String,
    @Parameter(description = "Offenders for which details are required, or get all.") @RequestParam(
      value = "offenderNo",
      required = false
    ) offenderNos: List<String>?
  ): List<OffenderKeyworkerDto> = keyworkerService.getOffenderKeyworkerDetailList(prisonId, offenderNos)

  @PostMapping(path = ["/{prisonId}/offenders"])
  fun getOffenderKeyworkerDetailsListPost(
    @Parameter(name = "prisonId", required = true) @PathVariable("prisonId") prisonId: String,
    @Parameter(description = "Offenders for which details are required, use GET version of endpoint if all offenders for prison are required.") @RequestBody offenderNos: List<String>?
  ): List<OffenderKeyworkerDto> {
    Validate.notEmpty<List<String>?>(offenderNos, "Please provide a list of Offender Nos.")
    return keyworkerService.getOffenderKeyworkerDetailList(prisonId, offenderNos)
  }

  @Operation(
    description = "All unallocated offenders in specified prison.",
    summary = "getUnallocatedOffenders"
  )
  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "200", description = "OK",
      ),
      ApiResponse(
        responseCode = "400",
        description = "Invalid request.",
        content =
        [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = ErrorResponse::class)
          )
        ],
      ),
      ApiResponse(
        responseCode = "404",
        description = "Requested resource not found.",
        content =
        [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = ErrorResponse::class)
          )
        ],
      ),
      ApiResponse(
        responseCode = "500",
        description = "Unrecoverable error occurred whilst processing request.",
        content =
        [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = ErrorResponse::class)
          )
        ],
      )
    ]
  )
  @GetMapping(path = ["/{prisonId}/offenders/unallocated"])
  fun getUnallocatedOffenders(
    @Parameter(name = "prisonId", required = true) @PathVariable("prisonId") prisonId: String,
    @Parameter(description = "Comma separated list of one or more of the following fields - <b>firstName, lastName</b>") @RequestHeader(
      value = PagingAndSortingDto.HEADER_SORT_FIELDS,
      defaultValue = ""
    ) sortFields: String,
    @Parameter(
      description = "Sort order (ASC or DESC) - defaults to ASC.",
      example = "ASC"
    ) @RequestHeader(value = PagingAndSortingDto.HEADER_SORT_ORDER, defaultValue = "ASC") sortOrder: SortOrder
  ): List<OffenderLocationDto> = keyworkerService.getUnallocatedOffenders(prisonId, sortFields, sortOrder)

  @Operation(description = "Key worker details.", summary = "getKeyworkerDetails")
  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "200",
        description = "OK",
      ), ApiResponse(
        responseCode = "400", description = "Invalid request.",
        content =
        [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = ErrorResponse::class)
          )
        ],
      ), ApiResponse(
        responseCode = "404",
        description = "Requested resource not found.",
        content =
        [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = ErrorResponse::class)
          )
        ],
      ), ApiResponse(
        responseCode = "500",
        description = "Unrecoverable error occurred whilst processing request.",
        content =
        [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = ErrorResponse::class)
          )
        ],
      )
    ]
  )
  @GetMapping(path = ["/{staffId}/prison/{prisonId}"])
  fun getKeyworkerDetails(
    @Parameter(name = "staffId", required = true) @PathVariable("staffId") staffId: Long,
    @Parameter(name = "prisonId", required = true) @PathVariable("prisonId") prisonId: String
  ): KeyworkerDto = keyworkerService.getKeyworkerDetails(prisonId, staffId)

  @Operation(
    description = "Offenders current Keyworker",
    summary = "getOffendersKeyworker"
  )
  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "200",
        description = "OK",
      ), ApiResponse(
        responseCode = "400", description = "Invalid request.",
        content =
        [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = ErrorResponse::class)
          )
        ],
      ), ApiResponse(
        responseCode = "404",
        description = "Requested resource not found.",
        content =
        [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = ErrorResponse::class)
          )
        ],
      ), ApiResponse(
        responseCode = "500",
        description = "Unrecoverable error occurred whilst processing request.",
        content =
        [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = ErrorResponse::class)
          )
        ],
      )
    ]
  )
  @GetMapping(path = ["/{prisonId}/offender/{offenderNo}"])
  @Deprecated("")
  /** Deprecated - don't need to pass in the prison id  */
  fun deprecated_getOffendersKeyworker(
    @Parameter(name = "prisonId", required = true) @PathVariable("prisonId") prisonId: String,
    @Parameter(name = "offenderNo", required = true) @PathVariable("offenderNo") offenderNo: String
  ): BasicKeyworkerDto =
    keyworkerService.getCurrentKeyworkerForPrisoner(offenderNo).orElseThrow { EntityNotFoundException() }

  @Operation(
    description = "Offenders current Keyworker",
    summary = "getOffendersKeyworker"
  )
  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "200",
        description = "OK",
      ), ApiResponse(
        responseCode = "400", description = "Invalid request.",
        content =
        [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = ErrorResponse::class)
          )
        ],
      ), ApiResponse(
        responseCode = "404",
        description = "Requested resource not found.",
        content =
        [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = ErrorResponse::class)
          )
        ],
      ), ApiResponse(
        responseCode = "500",
        description = "Unrecoverable error occurred whilst processing request.",
        content =
        [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = ErrorResponse::class)
          )
        ],
      )
    ]
  )
  @GetMapping(path = ["/offender/{offenderNo}"])
  fun getOffendersKeyworker(
    @Parameter(name = "offenderNo", required = true, example = "A1234BC") @PathVariable("offenderNo") offenderNo: String
  ): BasicKeyworkerDto =
    keyworkerService.getCurrentKeyworkerForPrisoner(offenderNo).orElseThrow { EntityNotFoundException() }

  @Operation(
    description = "Initiate auto-allocation process for specified prison.",
    summary = "autoAllocate"
  )
  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "200",
        description = "Request to initiate auto-allocation process has been successfully processed. (NOT YET IMPLEMENTED - Use returned process id to monitor process execution and outcome.) Note that until asynchronous processing is implemented, this request will execute synchronously and return total number of allocations processed.)",
      ), ApiResponse(
        responseCode = "404",
        description = "Prison id provided is not valid or is not accessible to user.",
        content =
        [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = ErrorResponse::class)
          )
        ],
      ), ApiResponse(
        responseCode = "409",
        description = "Auto-allocation processing not able to proceed or halted due to state of dependent resources.",
        content =
        [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = ErrorResponse::class)
          )
        ],
      ), ApiResponse(
        responseCode = "500",
        description = "Unrecoverable error occurred whilst processing request.",
        content =
        [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = ErrorResponse::class)
          )
        ],
      )
    ]
  )
  @PostMapping(path = ["/{prisonId}/allocate/start"])
  fun startAutoAllocation(
    @Parameter(name = "prisonId", required = true) @PathVariable("prisonId") prisonId: String
  ): Double = keyworkerAutoAllocationService.autoAllocate(prisonId).toDouble()

  @Operation(
    description = "Confirm allocations chosen by the auto-allocation process.",
    summary = "confirmAutoAllocation"
  )
  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "200",
        description = "Request to confirm allocations has been successfully processed. (NOT YET IMPLEMENTED - Use returned process id to monitor process execution and outcome.) Note that until asynchronous processing is implemented, this request will execute synchronously and return total number of allocations processed.)",
      ), ApiResponse(
        responseCode = "404",
        description = "Prison id provided is not valid or is not accessible to user.",
        content =
        [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = ErrorResponse::class)
          )
        ],
      ), ApiResponse(
        responseCode = "500",
        description = "Unrecoverable error occurred whilst processing request.",
        content =
        [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = ErrorResponse::class)
          )
        ],
      )
    ]
  )
  @PostMapping(path = ["/{prisonId}/allocate/confirm"])
  fun confirmAutoAllocation(
    @Parameter(name = "prisonId", required = true) @PathVariable("prisonId") prisonId: String
  ): Long = keyworkerAutoAllocationService.confirmAllocations(prisonId)

  @Operation(
    description = "Order by most recent first",
    summary = "getKeyWorkerHistoryForPrisoner"
  )
  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "200",
        description = "OK",
      ), ApiResponse(
        responseCode = "400", description = "Invalid request.",
        content =
        [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = ErrorResponse::class)
          )
        ],
      ), ApiResponse(
        responseCode = "404",
        description = "Requested resource not found.",
        content =
        [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = ErrorResponse::class)
          )
        ],
      ), ApiResponse(
        responseCode = "500",
        description = "Unrecoverable error occurred whilst processing request.",
        content =
        [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = ErrorResponse::class)
          )
        ],
      )
    ]
  )
  @GetMapping(path = ["/allocation-history/{offenderNo}"])
  fun getKeyWorkerHistoryForPrisoner(
    @Parameter(name = "offenderNo", required = true) @PathVariable("offenderNo") offenderNo: String
  ): OffenderKeyWorkerHistory =
    keyworkerService.getFullAllocationHistory(offenderNo).orElseThrow { EntityNotFoundException() }

  @Operation(
    description = "Gets a summary of the offender's allocation histories",
    summary = "getKeyWorkerHistorySummaryForPrisoners"
  )
  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "200", description = "OK",
      ), ApiResponse(
        responseCode = "400", description = "Invalid request.",
        content =
        [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = ErrorResponse::class)
          )
        ],
      ), ApiResponse(
        responseCode = "500",
        description = "Unrecoverable error occurred whilst processing request.",
        content =
        [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = ErrorResponse::class)
          )
        ],
      )
    ]
  )
  @PostMapping(path = ["/allocation-history/summary"])
  fun getKeyWorkerHistorySummaryForPrisoners(
    @RequestBody offenderNos: List<String>?
  ): List<OffenderKeyWorkerHistorySummary> {
    Validate.notEmpty<List<String>?>(offenderNos, "Please provide a list of Offender Nos.")
    return keyworkerService.getAllocationHistorySummary(offenderNos)
  }

  @Operation(
    description = "Marks the offender with expired time on active record",
    security = [ SecurityRequirement(name = "OMIC_ADMIN") ],
    summary = "deallocate"
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "200", description = "De allocated"), ApiResponse(
        responseCode = "400",
        description = "Invalid request.",
        content =
        [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = ErrorResponse::class)
          )
        ],
      ), ApiResponse(
        responseCode = "404", description = "Requested resource not found.",
        content =
        [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = ErrorResponse::class)
          )
        ],
      ), ApiResponse(
        responseCode = "500",
        description = "Unrecoverable error occurred whilst processing request.",
        content =
        [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = ErrorResponse::class)
          )
        ],
      )
    ]
  )
  @PutMapping(path = ["/deallocate/{offenderNo}"])
  @PreAuthorize("hasAnyRole('OMIC_ADMIN')")
  fun deallocate(
    @Parameter(name = "offenderNo", required = true) @PathVariable("offenderNo") offenderNo: String
  ) = keyworkerService.deallocate(offenderNo)

  @Operation(
    description = "Process manual allocation of an offender to a Key worker.",
    security = [SecurityRequirement(name = "OMIC_ADMIN")],
    summary = "allocate"
  )
  @ApiResponses(value = [ApiResponse(responseCode = "201", description = "The allocation has been created.")])
  @PostMapping(path = ["/allocate"], consumes = [MediaType.APPLICATION_JSON_VALUE])
  fun allocate(
    @Parameter(
      description = "New allocation details.",
      required = true
    ) @RequestBody keyworkerAllocation: @Valid KeyworkerAllocationDto
  ): ResponseEntity<*> {
    keyworkerService.allocate(keyworkerAllocation)
    return ResponseEntity.status(HttpStatus.CREATED).build<Any>()
  }

  @Operation(
    description = "Search for key workers within prison.",
    summary = "keyworkersearch"
  )
  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "200",
        description = "OK",
      ), ApiResponse(
        responseCode = "400", description = "Invalid request.",
        content =
        [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = ErrorResponse::class)
          )
        ],
      ), ApiResponse(
        responseCode = "404",
        description = "Requested resource not found.",
        content =
        [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = ErrorResponse::class)
          )
        ],
      ), ApiResponse(
        responseCode = "500",
        description = "Unrecoverable error occurred whilst processing request.",
        content =
        [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = ErrorResponse::class)
          )
        ],
      )
    ]
  )
  @GetMapping(path = ["/{prisonId}/members"])
  fun keyworkerSearch(
    @Parameter(name = "prisonId", required = true) @PathVariable("prisonId") prisonId: String,
    @Parameter(description = "Filter results by first name and/or last name of key worker. Supplied filter term is matched to start of key worker's first and last name.") @RequestParam(
      value = "nameFilter"
    ) nameFilter: Optional<String>?,
    @Parameter(description = "Filter results by status of key worker.") @RequestParam(value = "statusFilter") statusFilter: Optional<KeyworkerStatus>?,
    @Parameter(
      description = "Requested offset of first record in returned collection of allocation records.",
      example = "0"
    ) @RequestHeader(value = PagingAndSortingDto.HEADER_PAGE_OFFSET, defaultValue = "0") pageOffset: Long,
    @Parameter(description = "Requested limit to number of allocation records returned.", example = "10") @RequestHeader(
      value = PagingAndSortingDto.HEADER_PAGE_LIMIT,
      defaultValue = "1000"
    ) pageLimit: Long,
    @Parameter(description = "Comma separated list of one or more of the following fields - <b>firstName, lastName</b>") @RequestHeader(
      value = PagingAndSortingDto.HEADER_SORT_FIELDS,
      defaultValue = "lastName,firstName"
    ) sortFields: String,
    @Parameter(
      description = "Sort order (ASC or DESC) - defaults to ASC.",
      example = "ASC"
    ) @RequestHeader(value = PagingAndSortingDto.HEADER_SORT_ORDER, defaultValue = "ASC") sortOrder: SortOrder
  ): ResponseEntity<*> {
    val pageDto = PagingAndSortingDto
      .builder()
      .pageOffset(pageOffset)
      .pageLimit(pageLimit)
      .sortFields(sortFields)
      .sortOrder(sortOrder)
      .build()
    val activeKeyworkerPage = keyworkerService.getKeyworkers(prisonId, nameFilter, statusFilter, pageDto)
    return ResponseEntity(activeKeyworkerPage.items, activeKeyworkerPage.toHeaders(), HttpStatus.OK)
  }

  @Operation(
    description = "Specified key worker’s currently assigned offenders for given prison.",
    summary = "keyworkerallocations"
  )
  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "200",
        description = "OK",
        content =
        [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = KeyworkerAllocationDetailsDto::class)
          )
        ],
      ), ApiResponse(
        responseCode = "400", description = "Invalid request.",
        content =
        [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = ErrorResponse::class)
          )
        ],
      ), ApiResponse(
        responseCode = "404",
        description = "Requested resource not found.",
        content =
        [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = ErrorResponse::class)
          )
        ],
      ), ApiResponse(
        responseCode = "500",
        description = "Unrecoverable error occurred whilst processing request.",
        content =
        [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = ErrorResponse::class)
          )
        ],
      )
    ]
  )
  @GetMapping(path = ["/{staffId}/prison/{prisonId}/offenders"])
  fun getAllocationsForKeyworkerWithOffenderDetails(
    @Parameter(name = "staffId", required = true) @PathVariable("staffId") staffId: Long,
    @Parameter(name = "prisonId", required = true) @PathVariable("prisonId") prisonId: String,
    @Parameter(name = "skipOffenderDetails", example = "false") @RequestParam(
      value = "skipOffenderDetails",
      defaultValue = "false"
    ) skipOffenderDetails: Boolean
  ): List<KeyworkerAllocationDetailsDto> =
    keyworkerService.getAllocationsForKeyworkerWithOffenderDetails(prisonId, staffId, skipOffenderDetails)

  @Operation(
    description = "Add or update a key worker record",
    security = [SecurityRequirement(name = "OMIC_ADMIN")],
    summary = "addOrUpdateKeyworker"
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "201", description = "OK"), ApiResponse(
        responseCode = "400",
        description = "Invalid request.",
        content =
        [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = ErrorResponse::class)
          )
        ],
      ), ApiResponse(
        responseCode = "500",
        description = "Unrecoverable error occurred whilst processing request.",
        content =
        [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = ErrorResponse::class)
          )
        ],
      )
    ]
  )
  @PostMapping(path = ["/{staffId}/prison/{prisonId}"])
  fun addOrUpdateKeyworker(
    @Parameter(name = "staffId", required = true) @PathVariable("staffId") staffId: Long,
    @Parameter(name = "prisonId", required = true) @PathVariable("prisonId") prisonId: String,
    @Parameter(
      description = "New keyworker details.",
      required = true
    ) @RequestBody keyworkerUpdateDto: @Valid KeyworkerUpdateDto
  ) = keyworkerService.addOrUpdate(staffId, prisonId, keyworkerUpdateDto)

  @Operation(summary = "Get Prison Migration Status")
  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "200", description = "OK",
        content =
        [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = Prison::class)
          )
        ],
      ), ApiResponse(
        responseCode = "400",
        description = "Invalid request.",
        content =
        [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = ErrorResponse::class)
          )
        ],
      ), ApiResponse(
        responseCode = "404", description = "Requested resource not found.",
        content =
        [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = ErrorResponse::class)
          )
        ],
      ), ApiResponse(
        responseCode = "500",
        description = "Unrecoverable error occurred whilst processing request.",
        content =
        [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = ErrorResponse::class)
          )
        ],
      )
    ]
  )
  @GetMapping(path = ["/prison/{prisonId}"])
  fun getPrisonMigrationStatus(@Parameter(name = "prisonId") @PathVariable("prisonId") prisonId: String): Prison {
    val prisonDetail = prisonSupportedService.getPrisonDetail(prisonId)
    return prisonDetail ?: Prison.builder().prisonId(prisonId).supported(false).build()
  }
  @Operation(
    summary = "Enable Manual Allocation and Migrate",
    description = "Role Required: KW_MIGRATION. This will invoke migration from NOMIS DB"
  )
  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "200", description = "OK",
        content =
        [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = Prison::class)
          )
        ],
      ), ApiResponse(
        responseCode = "400",
        description = "Invalid request.",
        content =
        [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = ErrorResponse::class)
          )
        ],
      ), ApiResponse(
        responseCode = "500",
        description = "Unrecoverable error occurred whilst processing request.",
        content =
        [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = ErrorResponse::class)
          )
        ],
      )
    ]
  )
  @PostMapping(path = ["/enable/{prisonId}/manual"])
  fun addSupportedPrisonForManualAllocation(
    @Parameter(name = "prisonId") @PathVariable("prisonId") prisonId: String,
    @Parameter(name = "migrate") @RequestParam("migrate", defaultValue = "false") migrate: Boolean,
    @Parameter(
      name = "capacity",
      description = "standard and extended default keyworker capacities for this prison, comma separated, e.g. &capacity=6,9"
    ) @RequestParam("capacity") capacity: Array<Int>?,
    @Parameter(name = "frequency", description = "default KW Session Frequency in weeks (default 1)") @RequestParam(
      "frequency",
      defaultValue = "1"
    ) frequency: Int
  ): Prison = updateAndMigrate(prisonId, migrate, false, capacity, frequency)

  @Operation(
    summary = "Enable Auto Allocation for specified prison and Migrate",
    description = "Role Required: KW_MIGRATION. This will also invoke migration from NOMIS DB"
  )
  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "200", description = "OK",
        content =
        [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = Prison::class)
          )
        ],
      ), ApiResponse(
        responseCode = "400",
        description = "Invalid request.",
        content =
        [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = ErrorResponse::class)
          )
        ],
      ), ApiResponse(
        responseCode = "500",
        description = "Unrecoverable error occurred whilst processing request.",
        content =
        [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = ErrorResponse::class)
          )
        ],
      )
    ]
  )
  @PostMapping(path = ["/enable/{prisonId}/auto-allocate"])
  fun addSupportedPrisonForAutoAllocation(
    @Parameter(name = "prisonId") @PathVariable("prisonId") prisonId: String,
    @Parameter(name = "migrate") @RequestParam("migrate", defaultValue = "false") migrate: Boolean,
    @Parameter(
      name = "capacity",
      description = "standard and extended default keyworker capacities for this prison, comma separated, e.g. &capacity=6,9"
    ) @RequestParam("capacity") capacity: Array<Int>?,
    @Parameter(name = "frequency", description = "default KW Session Frequency in weeks (default 1)") @RequestParam(
      "frequency",
      defaultValue = "1"
    ) frequency: Int
  ): Prison {
    return updateAndMigrate(prisonId, migrate, true, capacity, frequency)
  }

  private fun updateAndMigrate(
    prisonId: String,
    migrate: Boolean,
    autoAllocate: Boolean,
    capacity: Array<Int>?,
    kwSessionFrequencyInWeeks: Int
  ): Prison {
    if (capacity != null) {
      Validate.isTrue(capacity.size == 2, "Two capacity values must be specified.")
      prisonSupportedService.updateSupportedPrison(
        prisonId,
        autoAllocate,
        capacity[0],
        capacity[1],
        kwSessionFrequencyInWeeks
      )
    } else {
      prisonSupportedService.updateSupportedPrison(prisonId, autoAllocate)
    }
    if (migrate) {
      keyworkerMigrationService.migrateKeyworkerByPrison(prisonId)
      roleMigrationService.migrate(prisonId)
    }
    return prisonSupportedService.getPrisonDetail(prisonId)
  }
}
