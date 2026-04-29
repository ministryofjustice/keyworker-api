package uk.gov.justice.digital.hmpps.keyworker.controllers

import io.swagger.v3.oas.annotations.Operation
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.keyworker.services.staff.KeyworkerStaffRoleSync

@RestController
@RequestMapping("/staff/keyworker-roles")
class KeyworkerStaffRoleSyncController(
  private val keyworkerStaffRoleSync: KeyworkerStaffRoleSync,
) {
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Sync keyworker staff roles from NOMIS", hidden = true)
  @PostMapping("/sync")
  fun syncKeyworkerStaffRoles() {
    keyworkerStaffRoleSync.syncKeyworkerStaffRoles()
  }
}
