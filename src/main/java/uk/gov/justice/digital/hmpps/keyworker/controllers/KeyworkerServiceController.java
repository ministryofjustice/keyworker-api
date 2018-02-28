package uk.gov.justice.digital.hmpps.keyworker.controllers;

import io.swagger.annotations.Api;
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

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping(
        value="key-worker",
        produces = MediaType.APPLICATION_JSON_VALUE)
@Api(tags = {"key-worker"})
public class KeyworkerServiceController {
    @Autowired
    private KeyworkerService keyworkerService;

    private static final Logger logger = LoggerFactory.getLogger(KeyworkerServiceController.class);


    @GetMapping(path = "/{agencyId}/available")
    public List<KeyworkerDto> getAvailableKeyworkers(@PathVariable(name = "agencyId") String agencyId) {
        logger.debug("finding available keyworkers for agency {}", agencyId);
        return keyworkerService.getAvailableKeyworkers(agencyId);
    }

    @GetMapping(path = "/{agencyId}/allocations")
    public List<KeyworkerAllocationDetailsDto> getKeyworkerAllocations(
            @PathVariable("agencyId") String agencyId,
            @RequestParam(value = "allocationType", required = false) Optional<AllocationType> allocationType,
            @RequestParam(value = "fromDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) Optional<LocalDate> fromDate,
            @RequestParam(value = "toDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) Optional<LocalDate> toDate,
            @RequestHeader(value = "Page-Offset", defaultValue = "0") Integer pageOffset,
            @RequestHeader(value = "Page-Limit", defaultValue = "10") Integer pageLimit,
            @RequestHeader(value = "Sort-Fields", defaultValue = "") String sortFields,
            @RequestHeader(value = "Sort-Order", defaultValue = "ASC") SortOrder sortOrder
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

    @GetMapping(path = "/{agencyId}/offenders/unallocated")
    public List<OffenderSummaryDto> getUnallocatedOffenders(
            @PathVariable("agencyId") String agencyId,
            @RequestHeader(value = "Page-Offset", defaultValue = "0") Integer pageOffset,
            @RequestHeader(value = "Page-Limit", defaultValue = "10") Integer pageLimit,
            @RequestHeader(value = "Sort-Fields", defaultValue = "") String sortFields,
            @RequestHeader(value = "Sort-Order", defaultValue = "ASC") SortOrder sortOrder
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

    @GetMapping(path="/{staffId}")
    public KeyworkerDto getKeyworkerDetails(@PathVariable("staffId") String staffId) {
        return keyworkerService.getKeyworkerDetails(staffId);
    }

    @PostMapping(path = "/{agencyId}/allocate/start")
    public String startAutoAllocation(@PathVariable("agencyId") String agencyId) {
        return keyworkerService.startAutoAllocation(agencyId);
    }

    @PostMapping(
            path = "/allocate",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity allocate(@RequestBody KeyworkerAllocationDto keyworkerAllocation) {
        keyworkerService.allocate(keyworkerAllocation);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

}
