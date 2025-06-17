package uk.gov.justice.digital.hmpps.keyworker.controllers

import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.keyworker.config.ALLOCATE_KEY_WORKERS
import uk.gov.justice.digital.hmpps.keyworker.config.CaseloadIdHeader
import uk.gov.justice.digital.hmpps.keyworker.config.MANAGE_ALLOCATIONS
import uk.gov.justice.digital.hmpps.keyworker.config.PolicyHeader
import uk.gov.justice.digital.hmpps.keyworker.dto.PersonStaffAllocations
import uk.gov.justice.digital.hmpps.keyworker.dto.RecommendedAllocations
import uk.gov.justice.digital.hmpps.keyworker.services.AllocationManager
import uk.gov.justice.digital.hmpps.keyworker.services.AllocationRecommender

@Tag(name = ALLOCATE_KEY_WORKERS)
@RestController
@RequestMapping("/prisons/{prisonCode}")
class ManageAllocationsController(
  private val allocationManager: AllocationManager,
  private val recommend: AllocationRecommender,
) {
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasRole('${Roles.KEYWORKER_RW}')")
  @PutMapping("/prisoners/keyworkers")
  fun manageKeyworkerAllocations(
    @PathVariable prisonCode: String,
    @RequestBody psa: PersonStaffAllocations,
  ) {
    allocationManager.manage(prisonCode, psa)
  }

  @PolicyHeader
  @CaseloadIdHeader
  @Tag(name = MANAGE_ALLOCATIONS)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasRole('${Roles.ALLOCATIONS_UI}')")
  @PutMapping("/prisoners/allocations")
  fun manageAllocations(
    @PathVariable prisonCode: String,
    @RequestBody psa: PersonStaffAllocations,
  ) {
    allocationManager.manage(prisonCode, psa)
  }

  @PolicyHeader
  @Tag(name = MANAGE_ALLOCATIONS)
  @PreAuthorize("hasRole('${Roles.ALLOCATIONS_UI}')")
  @GetMapping("/prisoners/allocation-recommendations")
  fun getAllocationRecommendations(
    @PathVariable prisonCode: String,
  ): RecommendedAllocations = recommend.allocations(prisonCode)
}
