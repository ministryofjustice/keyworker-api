package uk.gov.justice.digital.hmpps.keyworker.controllers

import io.swagger.v3.oas.annotations.Operation
import org.springframework.http.HttpStatus.NO_CONTENT
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.keyworker.config.PolicyHeader
import uk.gov.justice.digital.hmpps.keyworker.services.staff.TriggerKeyworkerSync

@RestController
@RequestMapping("/staff/keyworker-roles")
class KeyworkerStaffRoleSyncController(
  private val trigger: TriggerKeyworkerSync,
) {
  @PolicyHeader
  @Operation(hidden = true)
  @ResponseStatus(NO_CONTENT)
  @PostMapping("/sync")
  fun syncKeyworkerRoles() = trigger.syncKeyworkers()
}
