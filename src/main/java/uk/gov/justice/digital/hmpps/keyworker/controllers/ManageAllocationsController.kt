package uk.gov.justice.digital.hmpps.keyworker.controllers

import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.keyworker.config.ALLOCATE_KEY_WORKERS
import uk.gov.justice.digital.hmpps.keyworker.dto.PersonStaffAllocations
import uk.gov.justice.digital.hmpps.keyworker.services.KeyworkerAllocationManager

@Tag(name = ALLOCATE_KEY_WORKERS)
@RestController
@RequestMapping("/prisons/{prisonCode}")
class ManageAllocationsController(
  private val allocationManager: KeyworkerAllocationManager,
) {
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasRole('${Roles.KEYWORKER_RW}')")
  @PutMapping("/prisoners/keyworkers")
  fun manageAllocations(
    @PathVariable prisonCode: String,
    @RequestBody psa: PersonStaffAllocations,
  ) {
    allocationManager.manage(prisonCode, psa)
  }
}
