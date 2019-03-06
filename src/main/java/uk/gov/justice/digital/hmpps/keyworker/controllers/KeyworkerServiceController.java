package uk.gov.justice.digital.hmpps.keyworker.controllers;

import io.swagger.annotations.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.springframework.data.repository.query.Param;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uk.gov.justice.digital.hmpps.keyworker.dto.*;
import uk.gov.justice.digital.hmpps.keyworker.model.AllocationType;
import uk.gov.justice.digital.hmpps.keyworker.model.KeyworkerStatus;
import uk.gov.justice.digital.hmpps.keyworker.rolemigration.UserRolesMigrationService;
import uk.gov.justice.digital.hmpps.keyworker.services.KeyworkerAutoAllocationService;
import uk.gov.justice.digital.hmpps.keyworker.services.KeyworkerMigrationService;
import uk.gov.justice.digital.hmpps.keyworker.services.KeyworkerService;
import uk.gov.justice.digital.hmpps.keyworker.services.PrisonSupportedService;

import javax.persistence.EntityNotFoundException;
import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static uk.gov.justice.digital.hmpps.keyworker.dto.PagingAndSortingDto.*;

@Api(tags = {"key-worker"})

@RestController
@RequestMapping(
        value="key-worker",
        produces = MediaType.APPLICATION_JSON_VALUE)
@Slf4j
public class KeyworkerServiceController {

    private final KeyworkerService keyworkerService;
    private final KeyworkerMigrationService keyworkerMigrationService;
    private final UserRolesMigrationService roleMigrationService;
    private final KeyworkerAutoAllocationService keyworkerAutoAllocationService;
    private final PrisonSupportedService prisonSupportedService;

    public KeyworkerServiceController(final KeyworkerService keyworkerService,
                                      final KeyworkerMigrationService keyworkerMigrationService,
                                      final KeyworkerAutoAllocationService keyworkerAutoAllocationService,
                                      final UserRolesMigrationService roleMigrationService,
                                      final PrisonSupportedService prisonSupportedService) {
        this.keyworkerService = keyworkerService;
        this.keyworkerMigrationService = keyworkerMigrationService;
        this.keyworkerAutoAllocationService = keyworkerAutoAllocationService;
        this.roleMigrationService = roleMigrationService;
        this.prisonSupportedService = prisonSupportedService;
    }

    /* --------------------------------------------------------------------------------*/

    @ApiOperation(
            value = "Key workers available for allocation at specified prison.",
            notes = "Key workers available for allocation at specified prison.",
            nickname="getAvailableKeyworkers")

    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "OK", response = KeyworkerDto.class, responseContainer = "List"),
            @ApiResponse(code = 400, message = "Invalid request.", response = ErrorResponse.class, responseContainer = "List"),
            @ApiResponse(code = 404, message = "Requested resource not found.", response = ErrorResponse.class, responseContainer = "List"),
            @ApiResponse(code = 500, message = "Unrecoverable error occurred whilst processing request.", response = ErrorResponse.class, responseContainer = "List") })

    @GetMapping(path = "/{prisonId}/available")
    public List<KeyworkerDto> getAvailableKeyworkers(

            @ApiParam(value = "prisonId", required = true)
            @NotEmpty
            @PathVariable(name = "prisonId") final
            String prisonId) {

        log.debug("finding available key-workers for prison Id {}", prisonId);

        return keyworkerService.getAvailableKeyworkers(prisonId, true);
    }

    /* --------------------------------------------------------------------------------*/

    @ApiOperation(
            value = "Allocations in specified prison.",
            notes = "Allocations in specified prison.",
            nickname="getAllocations")

    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "OK", response = KeyworkerAllocationDetailsDto.class, responseContainer = "List"),
            @ApiResponse(code = 400, message = "Invalid request.", response = ErrorResponse.class, responseContainer = "List"),
            @ApiResponse(code = 404, message = "Requested resource not found.", response = ErrorResponse.class, responseContainer = "List"),
            @ApiResponse(code = 500, message = "Unrecoverable error occurred whilst processing request.", response = ErrorResponse.class, responseContainer = "List") })

    @GetMapping(path = "/{prisonId}/allocations")
    public ResponseEntity<List<KeyworkerAllocationDetailsDto>> getKeyworkerAllocations(
            @ApiParam(value = "prisonId", required = true)
            @NotEmpty
            @PathVariable("prisonId") final
            String prisonId,

            @ApiParam(value = "Optional filter by type of allocation. A for auto allocations, M for manual allocations.")
            @RequestParam(value = "allocationType", required = false) final
            String allocationType,

            @ApiParam(value = "Returned allocations must have been assigned on or after this date (in YYYY-MM-DD format).")
            @RequestParam(value = "fromDate",       required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) final
            Optional<LocalDate> fromDate,

            @ApiParam(value = "Returned allocations must have been assigned on or before this date (in YYYY-MM-DD format).", defaultValue="today's date")
            @RequestParam(value = "toDate",         required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) final
            Optional<LocalDate> toDate,

            @ApiParam(value = "Requested offset of first record in returned collection of allocation records.", defaultValue="0")
            @RequestHeader(value = HEADER_PAGE_OFFSET, defaultValue = "0") final
            Long pageOffset,

            @ApiParam(value = "Requested limit to number of allocation records returned.", defaultValue="10")
            @RequestHeader(value = HEADER_PAGE_LIMIT, defaultValue = "10") final
            Long pageLimit,

            @ApiParam(value = "Comma separated list of one or more of the following fields - <b>firstName, lastName, assigned</b>")
            @RequestHeader(value = HEADER_SORT_FIELDS, defaultValue = "") final
            String sortFields,

            @ApiParam(value = "Sort order (ASC or DESC) - defaults to ASC.", defaultValue="ASC")
            @RequestHeader(value = HEADER_SORT_ORDER, defaultValue = "ASC") final
            SortOrder sortOrder
    ) {
        final var page = keyworkerService.getAllocations(
                AllocationsFilterDto
                        .builder()
                        .prisonId(prisonId)
                        .allocationType(Optional.ofNullable(AllocationType.get(allocationType)))
                        .fromDate(fromDate)
                        .toDate(toDate.orElse(LocalDate.now()))
                        .build(),
                PagingAndSortingDto
                        .builder()
                        .pageOffset(pageOffset)
                        .pageLimit(pageLimit)
                        .sortFields(sortFields)
                        .sortOrder(sortOrder)
                        .build());

        return new ResponseEntity<>(page.getItems(), page.toHeaders(), HttpStatus.OK);
    }

    /* --------------------------------------------------------------------------------*/

    @ApiOperation(
            value = "Keyworker details for specified offenders in the given prison.",
            notes = "Keyworker details for specified offenders in the given prison, where the offender and details exist.",
            nickname="getOffenderForPrison")

    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "OK", response = OffenderKeyworkerDto.class, responseContainer = "List"),
            @ApiResponse(code = 400, message = "Invalid request.", response = ErrorResponse.class),
            @ApiResponse(code = 404, message = "Requested resource not found.", response = ErrorResponse.class),
            @ApiResponse(code = 500, message = "Unrecoverable error occurred whilst processing request.", response = ErrorResponse.class) })

    @GetMapping(path = "/{prisonId}/offenders")
    public List<OffenderKeyworkerDto> getOffenderKeyworkerDetailsList(
            @ApiParam(value = "prisonId", required = true)
            @NotEmpty
            @PathVariable("prisonId") final
            String prisonId,

            @ApiParam(value = "Offenders for which details are required, or get all.")
            @RequestParam(value = "offenderNo", required = false) final
            List<String> offenderNos
    ) {
        return keyworkerService.getOffenderKeyworkerDetailList(prisonId, offenderNos);
    }

    @PostMapping(path = "/{prisonId}/offenders")
    public List<OffenderKeyworkerDto> getOffenderKeyworkerDetailsListPost(
            @ApiParam(value = "prisonId", required = true)
            @NotEmpty
            @PathVariable("prisonId") final String prisonId,
            @ApiParam(value = "Offenders for which details are required, use GET version of endpoint if all offenders for prison are required.")
            @RequestBody @NotEmpty final List<String> offenderNos
    ) {
        Validate.notEmpty(offenderNos, "Please provide a list of Offender Nos.");
        return keyworkerService.getOffenderKeyworkerDetailList(prisonId, offenderNos);
    }

    /* --------------------------------------------------------------------------------*/

    @ApiOperation(
            value = "All unallocated offenders in specified prison.",
            notes = "All unallocated offenders in specified prison.",
            nickname="getUnallocatedOffenders")

    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "OK", response = OffenderLocationDto.class, responseContainer = "List"),
            @ApiResponse(code = 400, message = "Invalid request.", response = ErrorResponse.class, responseContainer = "List"),
            @ApiResponse(code = 404, message = "Requested resource not found.", response = ErrorResponse.class, responseContainer = "List"),
            @ApiResponse(code = 500, message = "Unrecoverable error occurred whilst processing request.", response = ErrorResponse.class, responseContainer = "List") })

    @GetMapping(path = "/{prisonId}/offenders/unallocated")
    public List<OffenderLocationDto> getUnallocatedOffenders(
            @ApiParam(value = "prisonId", required = true)
            @NotEmpty
            @PathVariable("prisonId") final
            String prisonId,

            @ApiParam(value = "Comma separated list of one or more of the following fields - <b>firstName, lastName</b>")
            @RequestHeader(value = HEADER_SORT_FIELDS, defaultValue = "") final
            String sortFields,

            @ApiParam(value = "Sort order (ASC or DESC) - defaults to ASC.", defaultValue="ASC")
            @RequestHeader(value = HEADER_SORT_ORDER, defaultValue = "ASC") final
            SortOrder sortOrder
    ) {
        return keyworkerService.getUnallocatedOffenders(prisonId, sortFields, sortOrder);
    }


    /* --------------------------------------------------------------------------------*/

    @ApiOperation(
            value = "Key worker details.",
            notes = "Key worker details.",
            nickname="getKeyworkerDetails")

    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "OK", response = KeyworkerDto.class),
            @ApiResponse(code = 400, message = "Invalid request.", response = ErrorResponse.class),
            @ApiResponse(code = 404, message = "Requested resource not found.", response = ErrorResponse.class),
            @ApiResponse(code = 500, message = "Unrecoverable error occurred whilst processing request.", response = ErrorResponse.class) })

    @GetMapping(path="/{staffId}/prison/{prisonId}")
    public KeyworkerDto getKeyworkerDetails(
            @ApiParam(value = "staffId", required = true)
            @NotEmpty
            @PathVariable("staffId") final
            Long staffId,

            @ApiParam(value = "prisonId", required = true)
            @NotEmpty
            @PathVariable("prisonId") final
            String prisonId) {

        return keyworkerService.getKeyworkerDetails(prisonId, staffId);
    }

    /* --------------------------------------------------------------------------------*/

    @ApiOperation(
            value = "Offenders current Keyworker",
            notes = "Offenders current Keyworker",
            nickname="getOffendersKeyworker")

    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "OK", response = BasicKeyworkerDto.class),
            @ApiResponse(code = 400, message = "Invalid request.", response = ErrorResponse.class),
            @ApiResponse(code = 404, message = "Requested resource not found.", response = ErrorResponse.class),
            @ApiResponse(code = 500, message = "Unrecoverable error occurred whilst processing request.", response = ErrorResponse.class) })

    @GetMapping(path="/{prisonId}/offender/{offenderNo}")

    public BasicKeyworkerDto getOffendersKeyworker(
            @ApiParam(value = "prisonId", required = true)
            @NotEmpty
            @PathVariable("prisonId") final
            String prisonId,
            @ApiParam(value = "offenderNo", required = true)
            @NotEmpty
            @PathVariable("offenderNo") final
            String offenderNo) {

        return keyworkerService.getCurrentKeyworkerForPrisoner(prisonId, offenderNo).orElseThrow(EntityNotFoundException::new);
    }

    /* --------------------------------------------------------------------------------*/

    @ApiOperation(
            value = "Initiate auto-allocation process for specified prison.",
            notes = "Initiate auto-allocation process for specified prison.",
            nickname="autoAllocate")

    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Request to initiate auto-allocation process has been successfully processed. (NOT YET IMPLEMENTED - Use returned process id to monitor process execution and outcome.) Note that until asynchronous processing is implemented, this request will execute synchronously and return total number of allocations processed.)", response = String.class),
            @ApiResponse(code = 404, message = "Prison id provided is not valid or is not accessible to user.", response = ErrorResponse.class),
            @ApiResponse(code = 409, message = "Auto-allocation processing not able to proceed or halted due to state of dependent resources.", response = ErrorResponse.class),
            @ApiResponse(code = 500, message = "Unrecoverable error occurred whilst processing request.", response = ErrorResponse.class) })

    @PostMapping(path = "/{prisonId}/allocate/start")

    public double startAutoAllocation(
            @ApiParam(value = "prisonId", required = true)
            @NotEmpty
            @PathVariable("prisonId") final String prisonId) {
        return keyworkerAutoAllocationService.autoAllocate(prisonId);
    }
    /* --------------------------------------------------------------------------------*/

    @ApiOperation(
            value = "Confirm allocations chosen by the auto-allocation process.",
            notes = "Confirm allocations chosen by the auto-allocation process.",
            nickname="confirmAutoAllocation")

    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Request to confirm allocations has been successfully processed. (NOT YET IMPLEMENTED - Use returned process id to monitor process execution and outcome.) Note that until asynchronous processing is implemented, this request will execute synchronously and return total number of allocations processed.)", response = String.class),
            @ApiResponse(code = 404, message = "Prison id provided is not valid or is not accessible to user.", response = ErrorResponse.class),
            @ApiResponse(code = 500, message = "Unrecoverable error occurred whilst processing request.", response = ErrorResponse.class) })

    @PostMapping(path = "/{prisonId}/allocate/confirm")

    public Long confirmAutoAllocation(
            @ApiParam(value = "prisonId", required = true)
            @NotEmpty
            @PathVariable("prisonId") final String prisonId) {
        return keyworkerAutoAllocationService.confirmAllocations(prisonId);
    }

    /* --------------------------------------------------------------------------------*/

    @ApiOperation(
            value = "Gets a complete history of the offenders allocations",
            notes = "Order by most recent first",
            nickname="getKeyWorkerHistoryForPrisoner")

    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "OK", response = OffenderKeyWorkerHistory.class),
            @ApiResponse(code = 400, message = "Invalid request.", response = ErrorResponse.class),
            @ApiResponse(code = 404, message = "Requested resource not found.", response = ErrorResponse.class),
            @ApiResponse(code = 500, message = "Unrecoverable error occurred whilst processing request.", response = ErrorResponse.class) })

    @GetMapping(path = "/allocation-history/{offenderNo}")
    public OffenderKeyWorkerHistory getKeyWorkerHistoryForPrisoner(
            @ApiParam(value = "offenderNo", required = true)
            @NotEmpty
            @PathVariable("offenderNo") final
            String offenderNo) {
        return keyworkerService.getFullAllocationHistory(offenderNo).orElseThrow(EntityNotFoundException::new);
    }

    /* --------------------------------------------------------------------------------*/

    @ApiOperation(
            value = "Deallocate a key worker from an offender",
            notes = "Marks the offender with expired time on active record",
            authorizations = { @Authorization("OMIC_ADMIN") },
            nickname="deallocate")

    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "De allocated"),
            @ApiResponse(code = 400, message = "Invalid request.", response = ErrorResponse.class),
            @ApiResponse(code = 404, message = "Requested resource not found.", response = ErrorResponse.class),
            @ApiResponse(code = 500, message = "Unrecoverable error occurred whilst processing request.", response = ErrorResponse.class) })

    @PutMapping(path = "/deallocate/{offenderNo}")
    public void deallocate(
            @ApiParam(value = "offenderNo", required = true)
            @NotEmpty
            @PathVariable("offenderNo") final
            String offenderNo) {
        keyworkerService.deallocate(offenderNo);
    }

    /* --------------------------------------------------------------------------------*/

    @ApiOperation(
            value = "Process manual allocation of an offender to a Key worker.",
            notes = "Process manual allocation of an offender to a Key worker.",
            authorizations = { @Authorization("OMIC_ADMIN") },
            nickname="allocate")

    @ApiResponses(value = {
            @ApiResponse(code = 201, message = "The allocation has been created.") })

    @PostMapping(
            path = "/allocate",
            consumes = MediaType.APPLICATION_JSON_VALUE)

    public ResponseEntity allocate(
            @ApiParam(value = "New allocation details." , required=true )
            @Valid
            @RequestBody final
            KeyworkerAllocationDto keyworkerAllocation) {

        keyworkerService.allocate(keyworkerAllocation);

        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /* --------------------------------------------------------------------------------*/

    @ApiOperation(
            value = "Search for key workers within prison.",
            notes = "Search for key workers using firstname or lastname",
            nickname="keyworkersearch")

    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "OK", response = KeyworkerDto.class, responseContainer = "List"),
            @ApiResponse(code = 400, message = "Invalid request.", response = ErrorResponse.class),
            @ApiResponse(code = 404, message = "Requested resource not found.", response = ErrorResponse.class),
            @ApiResponse(code = 500, message = "Unrecoverable error occurred whilst processing request.", response = ErrorResponse.class)  })

    @GetMapping(path="/{prisonId}/members")
    public ResponseEntity keyworkerSearch(
            @ApiParam(value = "prisonId", required = true)
            @NotEmpty
            @PathVariable("prisonId") final
            String prisonId,

            @ApiParam(value = "Filter results by first name and/or last name of key worker. Supplied filter term is matched to start of key worker's first and last name.")
            @RequestParam(value = "nameFilter", required = false) final
            Optional<String> nameFilter,

            @ApiParam(value = "Filter results by status of key worker.")
            @RequestParam(value = "statusFilter", required = false) final
            Optional<KeyworkerStatus> statusFilter,

            @ApiParam(value = "Requested offset of first record in returned collection of allocation records.", defaultValue="0")
            @RequestHeader(value = HEADER_PAGE_OFFSET, defaultValue = "0") final
            Long pageOffset,

            @ApiParam(value = "Requested limit to number of allocation records returned.", defaultValue="10")
            @RequestHeader(value = HEADER_PAGE_LIMIT, defaultValue = "1000") final
            Long pageLimit,

            @ApiParam(value = "Comma separated list of one or more of the following fields - <b>firstName, lastName</b>")
            @RequestHeader(value = HEADER_SORT_FIELDS, defaultValue = "lastName,firstName") final
            String sortFields,

            @ApiParam(value = "Sort order (ASC or DESC) - defaults to ASC.", defaultValue="ASC")
            @RequestHeader(value = HEADER_SORT_ORDER, defaultValue = "ASC") final
            SortOrder sortOrder) {

        final var pageDto = PagingAndSortingDto
                .builder()
                .pageOffset(pageOffset)
                .pageLimit(pageLimit)
                .sortFields(sortFields)
                .sortOrder(sortOrder)
                .build();

        final var activeKeyworkerPage = keyworkerService.getKeyworkers(prisonId, nameFilter, statusFilter, pageDto);
        return new ResponseEntity<>(activeKeyworkerPage.getItems(), activeKeyworkerPage.toHeaders(), HttpStatus.OK);

    }

    /* --------------------------------------------------------------------------------*/

    @ApiOperation(
            value = "Specified key worker’s currently assigned offenders for given prison.",
            notes = "Specified key worker’s currently assigned offenders for given prison.",
            nickname="keyworkerallocations")

    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "OK", response = KeyworkerAllocationDetailsDto.class, responseContainer = "List"),
            @ApiResponse(code = 400, message = "Invalid request.", response = ErrorResponse.class),
            @ApiResponse(code = 404, message = "Requested resource not found.", response = ErrorResponse.class),
            @ApiResponse(code = 500, message = "Unrecoverable error occurred whilst processing request.", response = ErrorResponse.class)  })

    @GetMapping(path="/{staffId}/prison/{prisonId}/offenders")
    public List<KeyworkerAllocationDetailsDto> getAllocationsForKeyworkerWithOffenderDetails(
            @ApiParam(value = "staffId", required = true)
            @NotEmpty
            @PathVariable("staffId") final
            Long staffId,

            @ApiParam(value = "prisonId", required = true)
            @NotEmpty
            @PathVariable("prisonId") final
            String prisonId,
            @ApiParam(value = "skipOffenderDetails", defaultValue = "false")
            @RequestParam(value = "skipOffenderDetails", required = false) final
            boolean skipOffenderDetails) {

        return keyworkerService.getAllocationsForKeyworkerWithOffenderDetails(prisonId, staffId, skipOffenderDetails);
    }

    @ApiOperation(
            value = "Add or update a key worker record",
            notes = "Staff members available capacity",
            authorizations = { @Authorization("OMIC_ADMIN") },
            nickname="addOrUpdateKeyworker")

    @ApiResponses(value = {
            @ApiResponse(code = 201, message = "OK"),
            @ApiResponse(code = 400, message = "Invalid request.", response = ErrorResponse.class),
            @ApiResponse(code = 500, message = "Unrecoverable error occurred whilst processing request.", response = ErrorResponse.class) })

    @PostMapping(path = "/{staffId}/prison/{prisonId}")
    public void addOrUpdateKeyworker(
            @ApiParam(value = "staffId", required = true)
            @NotEmpty
            @PathVariable("staffId") final
            Long staffId,

            @ApiParam(value = "prisonId", required = true)
            @NotEmpty
            @PathVariable("prisonId") final
            String prisonId,


            @ApiParam(value = "New keyworker details." , required=true )
            @Valid
            @RequestBody final
            KeyworkerUpdateDto keyworkerUpdateDto

    ) {
        keyworkerService.addOrUpdate(staffId, prisonId, keyworkerUpdateDto);
    }

    @ApiOperation(value = "Get Prison Migration Status")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "OK", response = Prison.class),
            @ApiResponse(code = 400, message = "Invalid request.", response = ErrorResponse.class),
            @ApiResponse(code = 404, message = "Requested resource not found.", response = ErrorResponse.class),
            @ApiResponse(code = 500, message = "Unrecoverable error occurred whilst processing request.", response = ErrorResponse.class) })

    @GetMapping(path="/prison/{prisonId}")
    public Prison getPrisonMigrationStatus(@ApiParam("prisonId") @NotEmpty @PathVariable("prisonId") final String prisonId) {
        final var prisonDetail = prisonSupportedService.getPrisonDetail(prisonId);
        return prisonDetail != null ? prisonDetail : Prison.builder().prisonId(prisonId).supported(false).build();
    }

    @ApiOperation(value = "Enable Manual Allocation and Migrate", notes = "Role Required: KW_MIGRATION. This will invoke migration from NOMIS DB")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "OK", response = Prison.class),
            @ApiResponse(code = 400, message = "Invalid request.", response = ErrorResponse.class),
            @ApiResponse(code = 500, message = "Unrecoverable error occurred whilst processing request.", response = ErrorResponse.class) })

    @PostMapping(path="/enable/{prisonId}/manual")
    public Prison addSupportedPrisonForManualAllocation(@ApiParam("prisonId") @NotEmpty @PathVariable("prisonId") final String prisonId,
                                                        @ApiParam("migrate") @Param("migrate") final boolean migrate,
                                                        @ApiParam(name = "capacity",
                                                                value = "standard and extended default keyworker capacities for this prison, comma separated, e.g. &capacity=6,9")
                                                        @Param("capacity") final Integer[] capacity,
                                                        @ApiParam(name = "frequency", value = "default KW Session Frequency in weeks (default 1)")
                                                        @Param("capacity") final Integer frequency) {
        return updateAndMigrate(prisonId, migrate, false, capacity, frequency);
    }

    @ApiOperation(value = "Enable Auto Allocation for specified prison and Migrate", notes = "Role Required: KW_MIGRATION. This will also invoke migration from NOMIS DB")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "OK", response = Prison.class),
            @ApiResponse(code = 400, message = "Invalid request.", response = ErrorResponse.class),
            @ApiResponse(code = 500, message = "Unrecoverable error occurred whilst processing request.", response = ErrorResponse.class) })

    @PostMapping(path="/enable/{prisonId}/auto-allocate")
    public Prison addSupportedPrisonForAutoAllocation(@ApiParam("prisonId") @NotEmpty @PathVariable("prisonId") final String prisonId,
                                                      @ApiParam("migrate") @Param("migrate") final boolean migrate,
                                                      @ApiParam(name = "capacity",
                                                              value = "standard and extended default keyworker capacities for this prison, comma separated, e.g. &capacity=6,9")
                                                      @Param("capacity") final Integer[] capacity,
                                                      @ApiParam(name = "frequency", value = "default KW Session Frequency in weeks (default 1)")
                                                      @Param("capacity") final Integer frequency) {
        return updateAndMigrate(prisonId, migrate, true, capacity, frequency);
    }


    private Prison updateAndMigrate(final String prisonId, final boolean migrate, final boolean autoAllocate, final Integer[] capacity, final Integer kwSessionFrequencyInWeeks) {

        if (capacity != null) {
            Validate.isTrue(capacity.length == 2, "Two capacity values must be specified.");
            prisonSupportedService.updateSupportedPrison(prisonId, autoAllocate, capacity[0], capacity[1], kwSessionFrequencyInWeeks);
        } else {
            prisonSupportedService.updateSupportedPrison(prisonId, autoAllocate);
        }

        if (migrate) {
            keyworkerMigrationService.migrateKeyworkerByPrison(prisonId);
            roleMigrationService.migrate(prisonId);
        }

        return prisonSupportedService.getPrisonDetail(prisonId);
    }

}
