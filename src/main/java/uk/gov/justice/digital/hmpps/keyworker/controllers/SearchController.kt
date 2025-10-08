package uk.gov.justice.digital.hmpps.keyworker.controllers

import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.keyworker.config.MANAGE_ALLOCATIONS
import uk.gov.justice.digital.hmpps.keyworker.config.MANAGE_STAFF
import uk.gov.justice.digital.hmpps.keyworker.config.PolicyHeader
import uk.gov.justice.digital.hmpps.keyworker.dto.AllocatableSearchRequest
import uk.gov.justice.digital.hmpps.keyworker.dto.AllocatableSearchResponse
import uk.gov.justice.digital.hmpps.keyworker.dto.RecordedEventRequest
import uk.gov.justice.digital.hmpps.keyworker.dto.RecordedEventResponse
import uk.gov.justice.digital.hmpps.keyworker.dto.person.PersonSearchRequest
import uk.gov.justice.digital.hmpps.keyworker.dto.person.PersonSearchResponse
import uk.gov.justice.digital.hmpps.keyworker.dto.staff.StaffSearchRequest
import uk.gov.justice.digital.hmpps.keyworker.dto.staff.StaffSearchResponse
import uk.gov.justice.digital.hmpps.keyworker.services.PersonSearch
import uk.gov.justice.digital.hmpps.keyworker.services.StaffSearch
import uk.gov.justice.digital.hmpps.keyworker.services.recordedevents.RecordedEventsSearch

@RestController
@RequestMapping(value = ["/search"])
class SearchController(
  private val staffSearch: StaffSearch,
  private val personSearch: PersonSearch,
  private val recordedEventSearch: RecordedEventsSearch,
) {
  @PolicyHeader
  @Tag(name = MANAGE_STAFF)
  @PreAuthorize("hasRole('${Roles.ALLOCATIONS_UI}')")
  @PostMapping("/prisons/{prisonCode}/staff-allocations")
  fun searchAllocatableStaff(
    @PathVariable prisonCode: String,
    @RequestBody request: AllocatableSearchRequest,
    @RequestParam(required = false, defaultValue = "false") includeStats: Boolean,
  ): AllocatableSearchResponse = staffSearch.searchForAllocatableStaff(prisonCode, request, includeStats)

  @PolicyHeader
  @Tag(name = MANAGE_STAFF)
  @PreAuthorize("hasRole('${Roles.ALLOCATIONS_UI}')")
  @PostMapping("/prisons/{prisonCode}/staff")
  fun searchStaff(
    @PathVariable prisonCode: String,
    @RequestBody request: StaffSearchRequest,
  ): StaffSearchResponse = staffSearch.searchForStaff(prisonCode, request)

  @PolicyHeader
  @Tag(name = MANAGE_STAFF)
  @PreAuthorize("hasRole('${Roles.ALLOCATIONS_UI}')")
  @PostMapping("/prisons/{prisonCode}/staff/{staffId}/recorded-events")
  fun searchStaffRecordedEvents(
    @PathVariable prisonCode: String,
    @PathVariable staffId: Long,
    @RequestBody request: RecordedEventRequest,
  ): RecordedEventResponse = recordedEventSearch.searchForAuthor(prisonCode, staffId, request)

  @PolicyHeader
  @Tag(name = MANAGE_ALLOCATIONS)
  @PreAuthorize("hasRole('${Roles.ALLOCATIONS_UI}')")
  @PostMapping("/prisons/{prisonCode}/prisoners")
  fun searchPeople(
    @PathVariable prisonCode: String,
    @RequestBody request: PersonSearchRequest,
  ): PersonSearchResponse = personSearch.findPeople(prisonCode, request)
}
