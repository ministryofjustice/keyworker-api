package uk.gov.justice.digital.hmpps.keyworker.controllers

import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.keyworker.config.ALLOCATE_KEY_WORKERS
import uk.gov.justice.digital.hmpps.keyworker.config.MANAGE_KEYWORKERS
import uk.gov.justice.digital.hmpps.keyworker.config.MANAGE_STAFF
import uk.gov.justice.digital.hmpps.keyworker.config.PolicyHeader
import uk.gov.justice.digital.hmpps.keyworker.dto.KeyworkerSearchRequest
import uk.gov.justice.digital.hmpps.keyworker.dto.KeyworkerSearchResponse
import uk.gov.justice.digital.hmpps.keyworker.dto.PersonSearchRequest
import uk.gov.justice.digital.hmpps.keyworker.dto.PersonSearchResponse
import uk.gov.justice.digital.hmpps.keyworker.dto.StaffSearchRequest
import uk.gov.justice.digital.hmpps.keyworker.dto.StaffSearchResponse
import uk.gov.justice.digital.hmpps.keyworker.services.KeyworkerSearch
import uk.gov.justice.digital.hmpps.keyworker.services.PersonSearch
import uk.gov.justice.digital.hmpps.keyworker.services.StaffSearch

@RestController
@RequestMapping(value = ["/search"])
class SearchController(
  private val keyworkerSearch: KeyworkerSearch,
  private val staffSearch: StaffSearch,
  private val personSearch: PersonSearch,
) {
  @Tag(name = MANAGE_KEYWORKERS)
  @PreAuthorize("hasRole('${Roles.KEYWORKER_RO}')")
  @PostMapping("/prisons/{prisonCode}/keyworkers")
  fun searchKeyworkers(
    @PathVariable prisonCode: String,
    @RequestBody request: KeyworkerSearchRequest,
  ): KeyworkerSearchResponse = keyworkerSearch.findKeyworkers(prisonCode, request)

  @PolicyHeader
  @Tag(name = MANAGE_STAFF)
  @PreAuthorize("hasRole('${Roles.ALLOCATIONS_UI}')")
  @PostMapping("/prisons/{prisonCode}/staff")
  fun searchStaff(
    @PathVariable prisonCode: String,
    @RequestBody request: StaffSearchRequest,
  ): StaffSearchResponse = staffSearch.searchForStaff(prisonCode, request)

  @Tag(name = ALLOCATE_KEY_WORKERS)
  @PreAuthorize("hasRole('${Roles.KEYWORKER_RO}')")
  @PostMapping("/prisons/{prisonCode}/prisoners")
  fun searchPeople(
    @PathVariable prisonCode: String,
    @RequestBody request: PersonSearchRequest,
  ): PersonSearchResponse = personSearch.findPeople(prisonCode, request)
}
