package uk.gov.justice.digital.hmpps.keyworker.controllers

import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import io.swagger.annotations.ApiResponse
import io.swagger.annotations.ApiResponses
import io.swagger.annotations.Authorization
import org.apache.commons.lang3.Validate
import org.slf4j.LoggerFactory
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import springfox.documentation.annotations.ApiIgnore
import uk.gov.justice.digital.hmpps.keyworker.dto.AllocationsFilterDto
import uk.gov.justice.digital.hmpps.keyworker.dto.BasicKeyworkerDto
import uk.gov.justice.digital.hmpps.keyworker.dto.ErrorResponse
import uk.gov.justice.digital.hmpps.keyworker.dto.KeyworkerAllocationDetailsDto
import uk.gov.justice.digital.hmpps.keyworker.dto.KeyworkerAllocationDto
import uk.gov.justice.digital.hmpps.keyworker.dto.KeyworkerDto
import uk.gov.justice.digital.hmpps.keyworker.dto.KeyworkerUpdateDto
import uk.gov.justice.digital.hmpps.keyworker.dto.OffenderKeyWorkerHistory
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

@Api(tags = ["key-worker"])
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

  @ApiOperation(
    value = "Key workers available for allocation at specified prison.",
    notes = "Key workers available for allocation at specified prison.",
    nickname = "getAvailableKeyworkers"
  )
  @ApiResponses(
    value = [
      ApiResponse(
        code = 200,
        message = "OK",
        response = KeyworkerDto::class,
        responseContainer = "List"
      ), ApiResponse(
        code = 400,
        message = "Invalid request.",
        response = ErrorResponse::class,
        responseContainer = "List"
      ), ApiResponse(
        code = 404,
        message = "Requested resource not found.",
        response = ErrorResponse::class,
        responseContainer = "List"
      ), ApiResponse(
        code = 500,
        message = "Unrecoverable error occurred whilst processing request.",
        response = ErrorResponse::class,
        responseContainer = "List"
      )
    ]
  )
  @GetMapping(path = ["/{prisonId}/available"])
  fun getAvailableKeyworkers(
    @ApiParam(value = "prisonId", required = true) @PathVariable(name = "prisonId") prisonId: String
  ): List<KeyworkerDto> {
    log.debug("finding available key-workers for prison Id {}", prisonId)
    return keyworkerService.getAvailableKeyworkers(prisonId, true)
  }

  @ApiOperation(
    value = "Allocations in specified prison.",
    notes = "Allocations in specified prison.",
    nickname = "getAllocations"
  )
  @ApiResponses(
    value = [
      ApiResponse(
        code = 200,
        message = "OK",
        response = KeyworkerAllocationDetailsDto::class,
        responseContainer = "List"
      ), ApiResponse(
        code = 400,
        message = "Invalid request.",
        response = ErrorResponse::class,
        responseContainer = "List"
      ), ApiResponse(
        code = 404,
        message = "Requested resource not found.",
        response = ErrorResponse::class,
        responseContainer = "List"
      ), ApiResponse(
        code = 500,
        message = "Unrecoverable error occurred whilst processing request.",
        response = ErrorResponse::class,
        responseContainer = "List"
      )
    ]
  )
  @GetMapping(path = ["/{prisonId}/allocations"])
  fun getKeyworkerAllocations(
    @ApiParam(value = "prisonId", required = true) @PathVariable("prisonId") prisonId: String,
    @ApiParam(value = "Optional filter by type of allocation. A for auto allocations, M for manual allocations.") @RequestParam(
      value = "allocationType",
      required = false
    ) allocationType: String?,
    @ApiParam(value = "Returned allocations must have been assigned on or after this date (in YYYY-MM-DD format).") @RequestParam(
      value = "fromDate",
      required = false
    ) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) fromDate: LocalDate?,
    @ApiParam(
      value = "Returned allocations must have been assigned on or before this date (in YYYY-MM-DD format).",
      defaultValue = "today's date"
    ) @RequestParam(
      value = "toDate",
      required = false
    ) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) toDate: LocalDate?,
    @ApiParam(
      value = "Requested offset of first record in returned collection of allocation records.",
      defaultValue = "0"
    ) @RequestHeader(value = PagingAndSortingDto.HEADER_PAGE_OFFSET, defaultValue = "0") pageOffset: Long,
    @ApiParam(value = "Requested limit to number of allocation records returned.", defaultValue = "10") @RequestHeader(
      value = PagingAndSortingDto.HEADER_PAGE_LIMIT,
      defaultValue = "10"
    ) pageLimit: Long,
    @ApiParam(value = "Comma separated list of one or more of the following fields - <b>firstName, lastName, assigned</b>") @RequestHeader(
      value = PagingAndSortingDto.HEADER_SORT_FIELDS,
      defaultValue = ""
    ) sortFields: String,
    @ApiParam(
      value = "Sort order (ASC or DESC) - defaults to ASC.",
      defaultValue = "ASC"
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

  @ApiOperation(
    value = "Keyworker details for specified offenders in the given prison.",
    notes = "Keyworker details for specified offenders in the given prison, where the offender and details exist.",
    nickname = "getOffenderForPrison"
  )
  @ApiResponses(
    value = [
      ApiResponse(
        code = 200,
        message = "OK",
        response = OffenderKeyworkerDto::class,
        responseContainer = "List"
      ), ApiResponse(code = 400, message = "Invalid request.", response = ErrorResponse::class), ApiResponse(
        code = 404,
        message = "Requested resource not found.",
        response = ErrorResponse::class
      ), ApiResponse(
        code = 500,
        message = "Unrecoverable error occurred whilst processing request.",
        response = ErrorResponse::class
      )
    ]
  )
  @GetMapping(path = ["/{prisonId}/offenders"])
  fun getOffenderKeyworkerDetailsList(
    @ApiParam(value = "prisonId", required = true) @PathVariable("prisonId") prisonId: String,
    @ApiParam(value = "Offenders for which details are required, or get all.") @RequestParam(
      value = "offenderNo",
      required = false
    ) offenderNos: List<String>?
  ): List<OffenderKeyworkerDto> = keyworkerService.getOffenderKeyworkerDetailList(prisonId, offenderNos)

  @PostMapping(path = ["/{prisonId}/offenders"])
  fun getOffenderKeyworkerDetailsListPost(
    @ApiParam(value = "prisonId", required = true) @PathVariable("prisonId") prisonId: String,
    @ApiParam(value = "Offenders for which details are required, use GET version of endpoint if all offenders for prison are required.") @RequestBody offenderNos: List<String>?
  ): List<OffenderKeyworkerDto> {
    Validate.notEmpty<List<String>?>(offenderNos, "Please provide a list of Offender Nos.")
    return keyworkerService.getOffenderKeyworkerDetailList(prisonId, offenderNos)
  }

  @ApiOperation(
    value = "All unallocated offenders in specified prison.",
    notes = "All unallocated offenders in specified prison.",
    nickname = "getUnallocatedOffenders"
  )
  @ApiResponses(
    value = [
      ApiResponse(code = 200, message = "OK", response = OffenderLocationDto::class, responseContainer = "List"),
      ApiResponse(
        code = 400,
        message = "Invalid request.",
        response = ErrorResponse::class,
        responseContainer = "List"
      ),
      ApiResponse(
        code = 404,
        message = "Requested resource not found.",
        response = ErrorResponse::class,
        responseContainer = "List"
      ),
      ApiResponse(
        code = 500,
        message = "Unrecoverable error occurred whilst processing request.",
        response = ErrorResponse::class,
        responseContainer = "List"
      )
    ]
  )
  @GetMapping(path = ["/{prisonId}/offenders/unallocated"])
  fun getUnallocatedOffenders(
    @ApiParam(value = "prisonId", required = true) @PathVariable("prisonId") prisonId: String,
    @ApiParam(value = "Comma separated list of one or more of the following fields - <b>firstName, lastName</b>") @RequestHeader(
      value = PagingAndSortingDto.HEADER_SORT_FIELDS,
      defaultValue = ""
    ) sortFields: String,
    @ApiParam(
      value = "Sort order (ASC or DESC) - defaults to ASC.",
      defaultValue = "ASC"
    ) @RequestHeader(value = PagingAndSortingDto.HEADER_SORT_ORDER, defaultValue = "ASC") sortOrder: SortOrder
  ): List<OffenderLocationDto> = keyworkerService.getUnallocatedOffenders(prisonId, sortFields, sortOrder)

  @ApiOperation(value = "Key worker details.", notes = "Key worker details.", nickname = "getKeyworkerDetails")
  @ApiResponses(
    value = [
      ApiResponse(
        code = 200,
        message = "OK",
        response = KeyworkerDto::class
      ), ApiResponse(code = 400, message = "Invalid request.", response = ErrorResponse::class), ApiResponse(
        code = 404,
        message = "Requested resource not found.",
        response = ErrorResponse::class
      ), ApiResponse(
        code = 500,
        message = "Unrecoverable error occurred whilst processing request.",
        response = ErrorResponse::class
      )
    ]
  )
  @GetMapping(path = ["/{staffId}/prison/{prisonId}"])
  fun getKeyworkerDetails(
    @ApiParam(value = "staffId", required = true) @PathVariable("staffId") staffId: Long,
    @ApiParam(value = "prisonId", required = true) @PathVariable("prisonId") prisonId: String
  ): KeyworkerDto = keyworkerService.getKeyworkerDetails(prisonId, staffId)

  @ApiIgnore
  @ApiOperation(
    value = "Offenders current Keyworker",
    notes = "Offenders current Keyworker",
    nickname = "getOffendersKeyworker"
  )
  @ApiResponses(
    value = [
      ApiResponse(
        code = 200,
        message = "OK",
        response = BasicKeyworkerDto::class
      ), ApiResponse(code = 400, message = "Invalid request.", response = ErrorResponse::class), ApiResponse(
        code = 404,
        message = "Requested resource not found.",
        response = ErrorResponse::class
      ), ApiResponse(
        code = 500,
        message = "Unrecoverable error occurred whilst processing request.",
        response = ErrorResponse::class
      )
    ]
  )
  @GetMapping(path = ["/{prisonId}/offender/{offenderNo}"])
  @Deprecated("")
  /** Deprecated - don't need to pass in the prison id  */
  fun deprecated_getOffendersKeyworker(
    @ApiParam(value = "prisonId", required = true) @PathVariable("prisonId") prisonId: String,
    @ApiParam(value = "offenderNo", required = true) @PathVariable("offenderNo") offenderNo: String
  ): BasicKeyworkerDto =
    keyworkerService.getCurrentKeyworkerForPrisoner(offenderNo).orElseThrow { EntityNotFoundException() }

  @ApiOperation(
    value = "Offenders current Keyworker",
    notes = "Offenders current Keyworker",
    nickname = "getOffendersKeyworker"
  )
  @ApiResponses(
    value = [
      ApiResponse(
        code = 200,
        message = "OK",
        response = BasicKeyworkerDto::class
      ), ApiResponse(code = 400, message = "Invalid request.", response = ErrorResponse::class), ApiResponse(
        code = 404,
        message = "Requested resource not found.",
        response = ErrorResponse::class
      ), ApiResponse(
        code = 500,
        message = "Unrecoverable error occurred whilst processing request.",
        response = ErrorResponse::class
      )
    ]
  )
  @GetMapping(path = ["/offender/{offenderNo}"])
  fun getOffendersKeyworker(
    @ApiParam(value = "offenderNo", required = true, example = "A1234BC") @PathVariable("offenderNo") offenderNo: String
  ): BasicKeyworkerDto =
    keyworkerService.getCurrentKeyworkerForPrisoner(offenderNo).orElseThrow { EntityNotFoundException() }

  @ApiOperation(
    value = "Initiate auto-allocation process for specified prison.",
    notes = "Initiate auto-allocation process for specified prison.",
    nickname = "autoAllocate"
  )
  @ApiResponses(
    value = [
      ApiResponse(
        code = 200,
        message = "Request to initiate auto-allocation process has been successfully processed. (NOT YET IMPLEMENTED - Use returned process id to monitor process execution and outcome.) Note that until asynchronous processing is implemented, this request will execute synchronously and return total number of allocations processed.)",
        response = String::class
      ), ApiResponse(
        code = 404,
        message = "Prison id provided is not valid or is not accessible to user.",
        response = ErrorResponse::class
      ), ApiResponse(
        code = 409,
        message = "Auto-allocation processing not able to proceed or halted due to state of dependent resources.",
        response = ErrorResponse::class
      ), ApiResponse(
        code = 500,
        message = "Unrecoverable error occurred whilst processing request.",
        response = ErrorResponse::class
      )
    ]
  )
  @PostMapping(path = ["/{prisonId}/allocate/start"])
  fun startAutoAllocation(
    @ApiParam(value = "prisonId", required = true) @PathVariable("prisonId") prisonId: String
  ): Double = keyworkerAutoAllocationService.autoAllocate(prisonId).toDouble()

  @ApiOperation(
    value = "Confirm allocations chosen by the auto-allocation process.",
    notes = "Confirm allocations chosen by the auto-allocation process.",
    nickname = "confirmAutoAllocation"
  )
  @ApiResponses(
    value = [
      ApiResponse(
        code = 200,
        message = "Request to confirm allocations has been successfully processed. (NOT YET IMPLEMENTED - Use returned process id to monitor process execution and outcome.) Note that until asynchronous processing is implemented, this request will execute synchronously and return total number of allocations processed.)",
        response = String::class
      ), ApiResponse(
        code = 404,
        message = "Prison id provided is not valid or is not accessible to user.",
        response = ErrorResponse::class
      ), ApiResponse(
        code = 500,
        message = "Unrecoverable error occurred whilst processing request.",
        response = ErrorResponse::class
      )
    ]
  )
  @PostMapping(path = ["/{prisonId}/allocate/confirm"])
  fun confirmAutoAllocation(
    @ApiParam(value = "prisonId", required = true) @PathVariable("prisonId") prisonId: String
  ): Long = keyworkerAutoAllocationService.confirmAllocations(prisonId)

  @ApiOperation(
    value = "Gets a complete history of the offenders allocations",
    notes = "Order by most recent first",
    nickname = "getKeyWorkerHistoryForPrisoner"
  )
  @ApiResponses(
    value = [
      ApiResponse(
        code = 200,
        message = "OK",
        response = OffenderKeyWorkerHistory::class
      ), ApiResponse(code = 400, message = "Invalid request.", response = ErrorResponse::class), ApiResponse(
        code = 404,
        message = "Requested resource not found.",
        response = ErrorResponse::class
      ), ApiResponse(
        code = 500,
        message = "Unrecoverable error occurred whilst processing request.",
        response = ErrorResponse::class
      )
    ]
  )
  @GetMapping(path = ["/allocation-history/{offenderNo}"])
  fun getKeyWorkerHistoryForPrisoner(
    @ApiParam(value = "offenderNo", required = true) @PathVariable("offenderNo") offenderNo: String
  ): OffenderKeyWorkerHistory =
    keyworkerService.getFullAllocationHistory(offenderNo).orElseThrow { EntityNotFoundException() }

  @ApiOperation(
    value = "Deallocate a key worker from an offender",
    notes = "Marks the offender with expired time on active record",
    authorizations = [Authorization("OMIC_ADMIN")],
    nickname = "deallocate"
  )
  @ApiResponses(
    value = [
      ApiResponse(code = 200, message = "De allocated"), ApiResponse(
        code = 400,
        message = "Invalid request.",
        response = ErrorResponse::class
      ), ApiResponse(code = 404, message = "Requested resource not found.", response = ErrorResponse::class), ApiResponse(
        code = 500,
        message = "Unrecoverable error occurred whilst processing request.",
        response = ErrorResponse::class
      )
    ]
  )
  @PutMapping(path = ["/deallocate/{offenderNo}"])
  fun deallocate(
    @ApiParam(value = "offenderNo", required = true) @PathVariable("offenderNo") offenderNo: String
  ) = keyworkerService.deallocate(offenderNo)

  @ApiOperation(
    value = "Process manual allocation of an offender to a Key worker.",
    notes = "Process manual allocation of an offender to a Key worker.",
    authorizations = [Authorization("OMIC_ADMIN")],
    nickname = "allocate"
  )
  @ApiResponses(value = [ApiResponse(code = 201, message = "The allocation has been created.")])
  @PostMapping(path = ["/allocate"], consumes = [MediaType.APPLICATION_JSON_VALUE])
  fun allocate(
    @ApiParam(
      value = "New allocation details.",
      required = true
    ) @RequestBody keyworkerAllocation: @Valid KeyworkerAllocationDto
  ): ResponseEntity<*> {
    keyworkerService.allocate(keyworkerAllocation)
    return ResponseEntity.status(HttpStatus.CREATED).build<Any>()
  }

  @ApiOperation(
    value = "Search for key workers within prison.",
    notes = "Search for key workers using firstname or lastname",
    nickname = "keyworkersearch"
  )
  @ApiResponses(
    value = [
      ApiResponse(
        code = 200,
        message = "OK",
        response = KeyworkerDto::class,
        responseContainer = "List"
      ), ApiResponse(code = 400, message = "Invalid request.", response = ErrorResponse::class), ApiResponse(
        code = 404,
        message = "Requested resource not found.",
        response = ErrorResponse::class
      ), ApiResponse(
        code = 500,
        message = "Unrecoverable error occurred whilst processing request.",
        response = ErrorResponse::class
      )
    ]
  )
  @GetMapping(path = ["/{prisonId}/members"])
  fun keyworkerSearch(
    @ApiParam(value = "prisonId", required = true) @PathVariable("prisonId") prisonId: String,
    @ApiParam(value = "Filter results by first name and/or last name of key worker. Supplied filter term is matched to start of key worker's first and last name.") @RequestParam(
      value = "nameFilter"
    ) nameFilter: Optional<String>?,
    @ApiParam(value = "Filter results by status of key worker.") @RequestParam(value = "statusFilter") statusFilter: Optional<KeyworkerStatus>?,
    @ApiParam(
      value = "Requested offset of first record in returned collection of allocation records.",
      defaultValue = "0"
    ) @RequestHeader(value = PagingAndSortingDto.HEADER_PAGE_OFFSET, defaultValue = "0") pageOffset: Long,
    @ApiParam(value = "Requested limit to number of allocation records returned.", defaultValue = "10") @RequestHeader(
      value = PagingAndSortingDto.HEADER_PAGE_LIMIT,
      defaultValue = "1000"
    ) pageLimit: Long,
    @ApiParam(value = "Comma separated list of one or more of the following fields - <b>firstName, lastName</b>") @RequestHeader(
      value = PagingAndSortingDto.HEADER_SORT_FIELDS,
      defaultValue = "lastName,firstName"
    ) sortFields: String,
    @ApiParam(
      value = "Sort order (ASC or DESC) - defaults to ASC.",
      defaultValue = "ASC"
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

  @ApiOperation(
    value = "Specified key worker’s currently assigned offenders for given prison.",
    notes = "Specified key worker’s currently assigned offenders for given prison.",
    nickname = "keyworkerallocations"
  )
  @ApiResponses(
    value = [
      ApiResponse(
        code = 200,
        message = "OK",
        response = KeyworkerAllocationDetailsDto::class,
        responseContainer = "List"
      ), ApiResponse(code = 400, message = "Invalid request.", response = ErrorResponse::class), ApiResponse(
        code = 404,
        message = "Requested resource not found.",
        response = ErrorResponse::class
      ), ApiResponse(
        code = 500,
        message = "Unrecoverable error occurred whilst processing request.",
        response = ErrorResponse::class
      )
    ]
  )
  @GetMapping(path = ["/{staffId}/prison/{prisonId}/offenders"])
  fun getAllocationsForKeyworkerWithOffenderDetails(
    @ApiParam(value = "staffId", required = true) @PathVariable("staffId") staffId: Long,
    @ApiParam(value = "prisonId", required = true) @PathVariable("prisonId") prisonId: String,
    @ApiParam(value = "skipOffenderDetails", defaultValue = "false") @RequestParam(
      value = "skipOffenderDetails",
      defaultValue = "false"
    ) skipOffenderDetails: Boolean
  ): List<KeyworkerAllocationDetailsDto> =
    keyworkerService.getAllocationsForKeyworkerWithOffenderDetails(prisonId, staffId, skipOffenderDetails)

  @ApiOperation(
    value = "Add or update a key worker record",
    notes = "Staff members available capacity",
    authorizations = [Authorization("OMIC_ADMIN")],
    nickname = "addOrUpdateKeyworker"
  )
  @ApiResponses(
    value = [
      ApiResponse(code = 201, message = "OK"), ApiResponse(
        code = 400,
        message = "Invalid request.",
        response = ErrorResponse::class
      ), ApiResponse(
        code = 500,
        message = "Unrecoverable error occurred whilst processing request.",
        response = ErrorResponse::class
      )
    ]
  )
  @PostMapping(path = ["/{staffId}/prison/{prisonId}"])
  fun addOrUpdateKeyworker(
    @ApiParam(value = "staffId", required = true) @PathVariable("staffId") staffId: Long,
    @ApiParam(value = "prisonId", required = true) @PathVariable("prisonId") prisonId: String,
    @ApiParam(
      value = "New keyworker details.",
      required = true
    ) @RequestBody keyworkerUpdateDto: @Valid KeyworkerUpdateDto
  ) = keyworkerService.addOrUpdate(staffId, prisonId, keyworkerUpdateDto)

  @ApiOperation(value = "Get Prison Migration Status")
  @ApiResponses(
    value = [
      ApiResponse(code = 200, message = "OK", response = Prison::class), ApiResponse(
        code = 400,
        message = "Invalid request.",
        response = ErrorResponse::class
      ), ApiResponse(code = 404, message = "Requested resource not found.", response = ErrorResponse::class), ApiResponse(
        code = 500,
        message = "Unrecoverable error occurred whilst processing request.",
        response = ErrorResponse::class
      )
    ]
  )
  @GetMapping(path = ["/prison/{prisonId}"])
  fun getPrisonMigrationStatus(@ApiParam("prisonId") @PathVariable("prisonId") prisonId: String): Prison {
    val prisonDetail = prisonSupportedService.getPrisonDetail(prisonId)
    return prisonDetail ?: Prison.builder().prisonId(prisonId).supported(false).build()
  }

  @ApiOperation(
    value = "Enable Manual Allocation and Migrate",
    notes = "Role Required: KW_MIGRATION. This will invoke migration from NOMIS DB"
  )
  @ApiResponses(
    value = [
      ApiResponse(code = 200, message = "OK", response = Prison::class), ApiResponse(
        code = 400,
        message = "Invalid request.",
        response = ErrorResponse::class
      ), ApiResponse(
        code = 500,
        message = "Unrecoverable error occurred whilst processing request.",
        response = ErrorResponse::class
      )
    ]
  )
  @PostMapping(path = ["/enable/{prisonId}/manual"])
  fun addSupportedPrisonForManualAllocation(
    @ApiParam("prisonId") @PathVariable("prisonId") prisonId: String,
    @ApiParam("migrate") @RequestParam("migrate", defaultValue = "false") migrate: Boolean,
    @ApiParam(
      name = "capacity",
      value = "standard and extended default keyworker capacities for this prison, comma separated, e.g. &capacity=6,9"
    ) @RequestParam("capacity") capacity: Array<Int>?,
    @ApiParam(name = "frequency", value = "default KW Session Frequency in weeks (default 1)") @RequestParam(
      "capacity",
      defaultValue = "1"
    ) frequency: Int
  ): Prison = updateAndMigrate(prisonId, migrate, false, capacity, frequency)

  @ApiOperation(
    value = "Enable Auto Allocation for specified prison and Migrate",
    notes = "Role Required: KW_MIGRATION. This will also invoke migration from NOMIS DB"
  )
  @ApiResponses(
    value = [
      ApiResponse(code = 200, message = "OK", response = Prison::class), ApiResponse(
        code = 400,
        message = "Invalid request.",
        response = ErrorResponse::class
      ), ApiResponse(
        code = 500,
        message = "Unrecoverable error occurred whilst processing request.",
        response = ErrorResponse::class
      )
    ]
  )
  @PostMapping(path = ["/enable/{prisonId}/auto-allocate"])
  fun addSupportedPrisonForAutoAllocation(
    @ApiParam("prisonId") @PathVariable("prisonId") prisonId: String,
    @ApiParam("migrate") @RequestParam("migrate", defaultValue = "false") migrate: Boolean,
    @ApiParam(
      name = "capacity",
      value = "standard and extended default keyworker capacities for this prison, comma separated, e.g. &capacity=6,9"
    ) @RequestParam("capacity") capacity: Array<Int>?,
    @ApiParam(name = "frequency", value = "default KW Session Frequency in weeks (default 1)") @RequestParam(
      "capacity",
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
