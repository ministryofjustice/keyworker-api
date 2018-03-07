package uk.gov.justice.digital.hmpps.keyworker.controllers;

import io.swagger.annotations.*;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.constraints.NotEmpty;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uk.gov.justice.digital.hmpps.keyworker.dto.*;
import uk.gov.justice.digital.hmpps.keyworker.model.AllocationType;
import uk.gov.justice.digital.hmpps.keyworker.services.KeyworkerService;

import javax.validation.Valid;
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

    public KeyworkerServiceController(KeyworkerService keyworkerService) {
        this.keyworkerService = keyworkerService;
    }

    /* --------------------------------------------------------------------------------*/

    @ApiOperation(
            value = "Key workers available for allocation at specified agency.",
            notes = "Key workers available for allocation at specified agency.",
            nickname="getAvailableKeyworkers")

    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "OK", response = KeyworkerDto.class, responseContainer = "List"),
            @ApiResponse(code = 400, message = "Invalid request.", response = ErrorResponse.class, responseContainer = "List"),
            @ApiResponse(code = 404, message = "Requested resource not found.", response = ErrorResponse.class, responseContainer = "List"),
            @ApiResponse(code = 500, message = "Unrecoverable error occurred whilst processing request.", response = ErrorResponse.class, responseContainer = "List") })

    @GetMapping(path = "/{agencyId}/available")
    public List<KeyworkerDto> getAvailableKeyworkers(

            @ApiParam(value = "agencyId", required = true)
            @NotEmpty
            @PathVariable(name = "agencyId")
                    String agencyId) {

        log.debug("finding available keyworkers for agency {}", agencyId);

        return keyworkerService.getAvailableKeyworkers(agencyId);
    }

    /* --------------------------------------------------------------------------------*/

    @ApiOperation(
            value = "Allocations in specified agency.",
            notes = "Allocations in specified agency.",
            nickname="getAllocations")

    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "OK", response = KeyworkerAllocationDetailsDto.class, responseContainer = "List"),
            @ApiResponse(code = 400, message = "Invalid request.", response = ErrorResponse.class, responseContainer = "List"),
            @ApiResponse(code = 404, message = "Requested resource not found.", response = ErrorResponse.class, responseContainer = "List"),
            @ApiResponse(code = 500, message = "Unrecoverable error occurred whilst processing request.", response = ErrorResponse.class, responseContainer = "List") })

    @GetMapping(path = "/{agencyId}/allocations")
    public ResponseEntity<List<KeyworkerAllocationDetailsDto>> getKeyworkerAllocations(
            @ApiParam(value = "agencyId", required = true)
            @NotEmpty
            @PathVariable("agencyId")
                    String agencyId,

            @ApiParam(value = "Optional filter by type of allocation. A for auto allocations, M for manual allocations.")
            @RequestParam(value = "allocationType", required = false)
                    String allocationType,

            @ApiParam(value = "Returned allocations must have been assigned on or after this date (in YYYY-MM-DD format).")
            @RequestParam(value = "fromDate",       required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    Optional<LocalDate> fromDate,

            @ApiParam(value = "Returned allocations must have been assigned on or before this date (in YYYY-MM-DD format).", defaultValue="today's date")
            @RequestParam(value = "toDate",         required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    Optional<LocalDate> toDate,

            @ApiParam(value = "Requested offset of first record in returned collection of allocation records.", defaultValue="0")
            @RequestHeader(value = HEADER_PAGE_OFFSET, defaultValue =   "0")
                    Long pageOffset,

            @ApiParam(value = "Requested limit to number of allocation records returned.", defaultValue="10")
            @RequestHeader(value = HEADER_PAGE_LIMIT,  defaultValue =  "10")
                    Long pageLimit,

            @ApiParam(value = "Comma separated list of one or more of the following fields - <b>firstName, lastName, assigned</b>")
            @RequestHeader(value = HEADER_SORT_FIELDS, defaultValue =    "")
                    String sortFields,

            @ApiParam(value = "Sort order (ASC or DESC) - defaults to ASC.", defaultValue="ASC")
            @RequestHeader(value = HEADER_SORT_ORDER,  defaultValue = "ASC")
                    SortOrder sortOrder
    ) {
        Page<KeyworkerAllocationDetailsDto> page = keyworkerService.getKeyworkerAllocations(
                AllocationsFilterDto
                        .builder()
                        .agencyId(agencyId)
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
            value = "All unallocated offenders in specified agency.",
            notes = "All unallocated offenders in specified agency.",
            nickname="getUnallocatedOffenders")

    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "OK", response = OffenderSummaryDto.class, responseContainer = "List"),
            @ApiResponse(code = 400, message = "Invalid request.", response = ErrorResponse.class, responseContainer = "List"),
            @ApiResponse(code = 404, message = "Requested resource not found.", response = ErrorResponse.class, responseContainer = "List"),
            @ApiResponse(code = 500, message = "Unrecoverable error occurred whilst processing request.", response = ErrorResponse.class, responseContainer = "List") })

    @GetMapping(path = "/{agencyId}/offenders/unallocated")
    public ResponseEntity<List<OffenderSummaryDto>> getUnallocatedOffenders(
            @ApiParam(value = "agencyId", required = true)
            @NotEmpty
            @PathVariable("agencyId")
                    String agencyId,

            @ApiParam(value = "Requested offset of first record in returned collection of unallocated records.", defaultValue="0")
            @RequestHeader(value = HEADER_PAGE_OFFSET, defaultValue =   "0")
                    Long pageOffset,

            @ApiParam(value = "Requested limit to number of unallocated records returned.", defaultValue="10")
            @RequestHeader(value = HEADER_PAGE_LIMIT,  defaultValue =  "10")
                    Long pageLimit,

            @ApiParam(value = "Comma separated list of one or more of the following fields - <b>firstName, lastName</b>")
            @RequestHeader(value = HEADER_SORT_FIELDS, defaultValue =    "")
                    String sortFields,

            @ApiParam(value = "Sort order (ASC or DESC) - defaults to ASC.", defaultValue="ASC")
            @RequestHeader(value = HEADER_SORT_ORDER,  defaultValue = "ASC")
                    SortOrder sortOrder
    ) {
        Page<OffenderSummaryDto> page = keyworkerService.getUnallocatedOffenders(agencyId,
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
            value = "Key worker details.",
            notes = "Key worker details.",
            nickname="getKeyworkerDetails")

    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "OK", response = KeyworkerDto.class),
            @ApiResponse(code = 400, message = "Invalid request.", response = ErrorResponse.class),
            @ApiResponse(code = 404, message = "Requested resource not found.", response = ErrorResponse.class),
            @ApiResponse(code = 500, message = "Unrecoverable error occurred whilst processing request.", response = ErrorResponse.class) })

    @GetMapping(path="/{staffId}")

    public KeyworkerDto getKeyworkerDetails(
            @ApiParam(value = "staffId", required = true)
            @NotEmpty
            @PathVariable("staffId")
                    Long staffId) {

        return keyworkerService.getKeyworkerDetails(staffId);
    }

    /* --------------------------------------------------------------------------------*/

    @ApiOperation(
            value = "Initiate auto-allocation process for specified agency.",
            notes = "Initiate auto-allocation process for specified agency.",
            nickname="autoAllocate")

    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Request to initiate auto-allocation process has been successfully processed. (NOT YET IMPLEMENTED - Use returned process id to monitor process execution and outcome.) Note that until asynchronous processing is implemented, this request will execute synchronously and return total number of allocations processed.)", response = String.class),
            @ApiResponse(code = 404, message = "Agency id provided is not valid or is not accessible to user.", response = ErrorResponse.class),
            @ApiResponse(code = 409, message = "Auto-allocation processing not able to proceed or halted due to state of dependent resources.", response = ErrorResponse.class),
            @ApiResponse(code = 500, message = "Unrecoverable error occurred whilst processing request.", response = ErrorResponse.class) })

    @PostMapping(path = "/{agencyId}/allocate/start")

    public String startAutoAllocation(
            @ApiParam(value = "agencyId", required = true)
            @NotEmpty
            @PathVariable("agencyId") String agencyId) {
        return keyworkerService.startAutoAllocation(agencyId);
    }

    /* --------------------------------------------------------------------------------*/

    @ApiOperation(
            value = "Process manual allocation of an offender to a Key worker.",
            notes = "Process manual allocation of an offender to a Key worker.",
            nickname="allocate")

    @ApiResponses(value = {
            @ApiResponse(code = 201, message = "The allocation has been created.") })

    @PostMapping(
            path = "/allocate",
            consumes = MediaType.APPLICATION_JSON_VALUE)

    public ResponseEntity allocate(
            @ApiParam(value = "New allocation details." , required=true )
            @Valid
            @RequestBody
                    KeyworkerAllocationDto keyworkerAllocation) {

        keyworkerService.allocate(keyworkerAllocation);

        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}
