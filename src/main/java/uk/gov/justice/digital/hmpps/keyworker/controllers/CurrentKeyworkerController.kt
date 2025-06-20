package uk.gov.justice.digital.hmpps.keyworker.controllers

import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.keyworker.config.ALLOCATE_KEY_WORKERS
import uk.gov.justice.digital.hmpps.keyworker.config.MANAGE_ALLOCATIONS
import uk.gov.justice.digital.hmpps.keyworker.dto.CurrentPersonStaffAllocation
import uk.gov.justice.digital.hmpps.keyworker.services.GetKeyworkerAllocations

@Tag(name = ALLOCATE_KEY_WORKERS)
@RestController
class CurrentKeyworkerController(
  private val allocations: GetKeyworkerAllocations,
) {
  @Tag(name = MANAGE_ALLOCATIONS)
  @PreAuthorize("hasRole('${Roles.KEYWORKER_RO}')")
  @GetMapping("prisons/{prisonCode}/prisoners/{prisonNumber}/keyworkers/current")
  fun getCurrentKeyworker(
    @PathVariable prisonCode: String,
    @PathVariable prisonNumber: String,
  ): CurrentPersonStaffAllocation = allocations.currentFor(prisonCode, prisonNumber)
}
