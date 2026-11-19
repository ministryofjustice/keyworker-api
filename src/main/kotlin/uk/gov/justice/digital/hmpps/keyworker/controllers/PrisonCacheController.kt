package uk.gov.justice.digital.hmpps.keyworker.controllers

import io.swagger.v3.oas.annotations.Operation
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.keyworker.services.PrisonCache

@RestController
@RequestMapping
class PrisonCacheController(
  private val prisonCache: PrisonCache,
) {
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Reconcile prison cache", hidden = true)
  @PostMapping(value = ["/prison-cache"])
  fun reconcilePrisonCache() {
    prisonCache.reconcileAll()
  }
}
