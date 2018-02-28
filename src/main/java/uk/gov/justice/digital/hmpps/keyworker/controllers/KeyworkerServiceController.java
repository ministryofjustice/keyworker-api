package uk.gov.justice.digital.hmpps.keyworker.controllers;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uk.gov.justice.digital.hmpps.keyworker.dto.*;
import uk.gov.justice.digital.hmpps.keyworker.services.KeyworkerService;

import javax.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Api(tags = {"key-worker"})

@RestController
@RequestMapping(
        value="key-worker",
        produces = MediaType.APPLICATION_JSON_VALUE)
public class KeyworkerServiceController {

    private static final Logger logger = LoggerFactory.getLogger(KeyworkerServiceController.class);

    private final KeyworkerService keyworkerService;

    @Autowired
    public KeyworkerServiceController(KeyworkerService keyworkerService) {
        this.keyworkerService = keyworkerService;
    }


    @ApiOperation(
            value = "Key workers available for allocation at specified agency.",
            notes = "Key workers available for allocation at specified agency.",
            nickname="getAvailableKeyworkers")

    @GetMapping(path = "/{agencyId}/available")
    public List<KeyworkerDto> getAvailableKeyworkers(@PathVariable(name = "agencyId") String agencyId) {
        logger.debug("finding available keyworkers for agency {}", agencyId);
        return keyworkerService.getAvailableKeyworkers(agencyId);
    }


    @ApiOperation(
            value = "Allocations in specified agency.",
            notes = "Allocations in specified agency.",
            nickname="getAllocations")

    @GetMapping(path = "/{agencyId}/allocations")
    public List<KeyworkerAllocationDetailsDto> getKeyworkerAllocations(
            @PathVariable("agencyId") String agencyId,
            @RequestParam(value = "allocationType", required = false) Optional<AllocationType> allocationType,
            @RequestParam(value = "fromDate",       required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) Optional<LocalDate> fromDate,
            @RequestParam(value = "toDate",         required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) Optional<LocalDate> toDate,
            @RequestHeader(value = "Page-Offset", defaultValue =   "0") Integer pageOffset,
            @RequestHeader(value = "Page-Limit",  defaultValue =  "10") Integer pageLimit,
            @RequestHeader(value = "Sort-Fields", defaultValue =    "") String sortFields,
            @RequestHeader(value = "Sort-Order",  defaultValue = "ASC") SortOrder sortOrder
    ) {
        return keyworkerService.getKeyworkerAllocations(
                AllocationsFilterDto
                        .builder()
                        .agencyId(agencyId)
                        .allocationType(allocationType)
                        .fromDate(fromDate)
                        .toDate(toDate)
                        .build(),
                PagingAndSortingDto
                        .builder()
                        .pageOffset(pageOffset)
                        .pageLimit(pageLimit)
                        .sortFields(sortFields)
                        .sortOrder(sortOrder)
                        .build()
        );
    }


    @ApiOperation(
            value = "All unallocated offenders in specified agency.",
            notes = "All unallocated offenders in specified agency.",
            nickname="getUnallocatedOffenders")

    @GetMapping(path = "/{agencyId}/offenders/unallocated")
    public List<OffenderSummaryDto> getUnallocatedOffenders(
            @PathVariable("agencyId") String agencyId,
            @RequestHeader(value = "Page-Offset", defaultValue =   "0") Integer pageOffset,
            @RequestHeader(value = "Page-Limit",  defaultValue =  "10") Integer pageLimit,
            @RequestHeader(value = "Sort-Fields", defaultValue =    "") String sortFields,
            @RequestHeader(value = "Sort-Order",  defaultValue = "ASC") SortOrder sortOrder
    ) {
        return keyworkerService.getUnallocatedOffenders(
                agencyId,
                PagingAndSortingDto
                        .builder()
                        .pageOffset(pageOffset)
                        .pageLimit(pageLimit)
                        .sortFields(sortFields)
                        .sortOrder(sortOrder)
                        .build()
        );
    }


    @ApiOperation(
            value = "Key worker details.",
            notes = "Key worker details.",
            nickname="getKeyworkerDetails")

    @GetMapping(path="/{staffId}")
    public KeyworkerDto getKeyworkerDetails(@PathVariable("staffId") String staffId) {
        return keyworkerService.getKeyworkerDetails(staffId);
    }


    @ApiOperation(
            value = "Initiate auto-allocation process for specified agency.",
            notes = "Initiate auto-allocation process for specified agency.",
            nickname="autoAllocate")

    @PostMapping(path = "/{agencyId}/allocate/start")
    public String startAutoAllocation(@PathVariable("agencyId") String agencyId) {
        return keyworkerService.startAutoAllocation(agencyId);
    }


    @ApiOperation(
            value = "Process manual allocation of an offender to a Key worker.",
            notes = "Process manual allocation of an offender to a Key worker.",
            nickname="allocate")

    @PostMapping(
            path = "/allocate",
            consumes = MediaType.APPLICATION_JSON_VALUE)

    public ResponseEntity allocate(@Valid @RequestBody KeyworkerAllocationDto keyworkerAllocation) {
        keyworkerService.allocate(keyworkerAllocation);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

}
