package uk.gov.justice.digital.hmpps.keyworker.controllers

import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.keyworker.config.ALLOCATE_KEY_WORKERS
import uk.gov.justice.digital.hmpps.keyworker.config.MANAGE_ALLOCATIONS
import uk.gov.justice.digital.hmpps.keyworker.config.PolicyHeader
import uk.gov.justice.digital.hmpps.keyworker.dto.KeyworkerAllocationHistory
import uk.gov.justice.digital.hmpps.keyworker.dto.StaffAllocationHistory
import uk.gov.justice.digital.hmpps.keyworker.services.GetAllocations
import uk.gov.justice.digital.hmpps.keyworker.services.GetKeyworkerAllocations

@RestController
@RequestMapping("/prisoners/{prisonNumber}")
class GetAllocationsController(
  private val allocations: GetAllocations,
  private val keyworkerAllocations: GetKeyworkerAllocations,
) {
  @Tag(name = ALLOCATE_KEY_WORKERS)
  @PreAuthorize("hasRole('${Roles.KEYWORKER_RO}')")
  @GetMapping("/keyworkers")
  fun getKeyworkerAllocations(
    @PathVariable prisonNumber: String,
  ): KeyworkerAllocationHistory = keyworkerAllocations.historyFor(prisonNumber)

  @PolicyHeader
  @Tag(name = MANAGE_ALLOCATIONS)
  @PreAuthorize("hasRole('${Roles.ALLOCATIONS_UI}')")
  @GetMapping("/allocations")
  fun getAllocations(
    @PathVariable prisonNumber: String,
  ): StaffAllocationHistory = allocations.historyFor(prisonNumber)
}
