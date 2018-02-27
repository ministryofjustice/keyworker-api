package uk.gov.justice.digital.hmpps.keyworker.controllers;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import uk.gov.justice.digital.hmpps.keyworker.dto.*;
import uk.gov.justice.digital.hmpps.keyworker.services.KeyworkerService;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.springframework.web.bind.annotation.RequestMethod.GET;

@RestController
@RequestMapping(
        value="key-worker",
        produces = MediaType.APPLICATION_JSON_VALUE)
@Api(tags = {"key-worker"})
public class KeyworkerServiceController {
    @Autowired
    private KeyworkerService keyworkerService;

    private static final Logger logger = LoggerFactory.getLogger(KeyworkerServiceController.class);

    @GetMapping(path="/{staffUsername}")
    @ApiOperation(value = "Get offenders of a keyworker")
    public List<OffenderKeyworkerDto> getOffendersForKeyworker(@PathVariable("staffUsername") String staffUsername) {
        logger.debug("Looking up data for staff username {}", staffUsername);

        return keyworkerService.getOffendersForKeyworker(staffUsername);
    }

    @GetMapping(path = "/{agencyId}/available")
    public List<KeyworkerDto> getAvailableKeyworkers(@PathVariable(name = "agencyId") String agencyId) {
        logger.debug("finding available keyworkers for agency {}", agencyId);
        return keyworkerService.getAvailableKeyworkers(agencyId);
    }

    @GetMapping(path = "/{agencyId}/allocations")
    public List<KeyworkerAllocationDto> getKeyworkerAllocations(
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
                AllocationsRequestDto
                        .builder()
                        .agencyId(agencyId)
                        .allocationType(allocationType)
                        .fromDate(fromDate)
                        .toDate(toDate)
                        .build(),
                PageDto
                        .builder()
                        .pageOffset(pageOffset)
                        .pageLimit(pageLimit)
                        .sortFields(sortFields)
                        .sortOrder(sortOrder)
                .build()
        );
    }

}
