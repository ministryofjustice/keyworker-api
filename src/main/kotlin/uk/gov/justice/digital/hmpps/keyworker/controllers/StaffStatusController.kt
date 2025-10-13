package uk.gov.justice.digital.hmpps.keyworker.controllers

import io.swagger.v3.oas.annotations.Operation
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationPolicy
import uk.gov.justice.digital.hmpps.keyworker.services.ReactivateStaff
import java.time.LocalDate

@RestController
@RequestMapping
class StaffStatusController(
  private val reactivateStaff: ReactivateStaff,
) {
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Reactivate staff returning from leave", hidden = true)
  @PostMapping(value = ["/staff/returning-from-leave"])
  fun makeActiveReturningFromLeave(
    @RequestParam(required = false) date: LocalDate?,
  ) {
    AllocationPolicy.entries.forEach { policy ->
      reactivateStaff.returningFromLeave(policy, date ?: LocalDate.now())
    }
  }
}
