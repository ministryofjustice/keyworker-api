package uk.gov.justice.digital.hmpps.keyworker.controllers

import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.keyworker.config.ALLOCATE_KEY_WORKERS
import uk.gov.justice.digital.hmpps.keyworker.dto.PersonStaffAllocationHistory
import uk.gov.justice.digital.hmpps.keyworker.services.GetKeyworkerAllocations

@Tag(name = ALLOCATE_KEY_WORKERS)
@RestController
@RequestMapping("/prisoners/{prisonNumber}")
class GetAllocationController(
  private val keyworkerAllocations: GetKeyworkerAllocations,
) {
  @PreAuthorize("hasRole('${Roles.KEYWORKER_RO}')")
  @GetMapping("/keyworkers")
  fun getKeyworkerAllocations(
    @PathVariable prisonNumber: String,
  ): PersonStaffAllocationHistory = keyworkerAllocations.allocationHistoryFor(prisonNumber)
}
