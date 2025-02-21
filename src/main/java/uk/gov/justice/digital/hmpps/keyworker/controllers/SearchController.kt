package uk.gov.justice.digital.hmpps.keyworker.controllers

import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.keyworker.config.ALLOCATE_KEY_WORKERS
import uk.gov.justice.digital.hmpps.keyworker.dto.KeyworkerSearchRequest
import uk.gov.justice.digital.hmpps.keyworker.dto.KeyworkerSearchResponse
import uk.gov.justice.digital.hmpps.keyworker.services.KeyworkerSearch

@Tag(name = ALLOCATE_KEY_WORKERS)
@RestController
@RequestMapping(value = ["/search"])
class SearchController(
  private val search: KeyworkerSearch,
) {
  @PreAuthorize("hasRole('${Roles.KEYWORKER_RO}')")
  @PostMapping("/prisons/{prisonCode}/keyworkers")
  fun searchKeyworkers(
    @PathVariable prisonCode: String,
    @RequestBody request: KeyworkerSearchRequest,
  ): KeyworkerSearchResponse = search.findKeyworkers(prisonCode, request)
}
