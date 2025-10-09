package uk.gov.justice.digital.hmpps.keyworker.controllers

import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.keyworker.config.MANAGE_ALLOCATIONS
import uk.gov.justice.digital.hmpps.keyworker.model.person.CurrentPersonStaffAllocation
import uk.gov.justice.digital.hmpps.keyworker.services.GetCurrentAllocations

@RestController
class CurrentAllocationController(
  private val allocations: GetCurrentAllocations,
) {
  @Tag(name = MANAGE_ALLOCATIONS)
  @PreAuthorize("hasRole('${Roles.ALLOCATIONS_RO}')")
  @GetMapping("/prisoners/{personIdentifier}/allocations/current")
  fun getCurrentAllocation(
    @PathVariable personIdentifier: String,
    @RequestParam(required = false, defaultValue = "false") includeContactDetails: Boolean,
  ): CurrentPersonStaffAllocation = allocations.currentFor(personIdentifier, includeContactDetails)
}
