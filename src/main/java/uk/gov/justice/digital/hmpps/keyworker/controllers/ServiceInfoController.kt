package uk.gov.justice.digital.hmpps.keyworker.controllers

import io.swagger.v3.oas.annotations.Operation
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.keyworker.dto.prison.ActiveAgenciesResponse
import uk.gov.justice.digital.hmpps.keyworker.services.PrisonService

@RestController
@RequestMapping("/{policy}/info", produces = [MediaType.APPLICATION_JSON_VALUE])
class ServiceInfoController(
  private val prisonService: PrisonService,
) {
  @Operation(hidden = true)
  @GetMapping
  fun getPolicyInfo(
    @PathVariable policy: String,
  ): ActiveAgenciesResponse = ActiveAgenciesResponse(prisonService.findPolicyEnabledPrisons(policy))
}
