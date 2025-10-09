package uk.gov.justice.digital.hmpps.keyworker.controllers

import io.swagger.v3.oas.annotations.Operation
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationPolicy
import uk.gov.justice.digital.hmpps.keyworker.model.person.CurrentPersonStaffAllocation
import uk.gov.justice.digital.hmpps.keyworker.services.GetCurrentAllocations

@RestController
class DeprecatedEndpointController(
  private val allocations: GetCurrentAllocations,
) {
  @Operation(deprecated = true)
  @GetMapping("/key-worker/offender/{offenderNo}")
  fun deprecatedGetOffendersKeyworker(
    @PathVariable("offenderNo") personIdentifier: String,
  ): ResponseEntity<BasicKeyworkerInfo> =
    allocations.currentFor(personIdentifier, true).asBasicKeyworkerInfo()?.let {
      ResponseEntity.ok(it)
    } ?: ResponseEntity.notFound().build()
}

class BasicKeyworkerInfo(
  val staffId: @NotNull Long,
  val firstName: @NotBlank String,
  val lastName: @NotBlank String,
  val email: String?,
)

private fun CurrentPersonStaffAllocation.asBasicKeyworkerInfo(): BasicKeyworkerInfo? {
  val staff =
    allocations
      .firstOrNull {
        it.policy.code == AllocationPolicy.KEY_WORKER.name
      }?.staffMember
  return staff?.let {
    BasicKeyworkerInfo(it.staffId, it.firstName, it.lastName, it.emailAddresses.firstOrNull())
  }
}
