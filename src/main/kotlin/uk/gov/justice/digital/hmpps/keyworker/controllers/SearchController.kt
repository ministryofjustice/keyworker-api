package uk.gov.justice.digital.hmpps.keyworker.controllers

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
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
import uk.gov.justice.digital.hmpps.keyworker.config.StandardAoiErrorResponse
import uk.gov.justice.digital.hmpps.keyworker.model.RecordedEventRequest
import uk.gov.justice.digital.hmpps.keyworker.model.RecordedEventResponse
import uk.gov.justice.digital.hmpps.keyworker.model.person.PersonSearchRequest
import uk.gov.justice.digital.hmpps.keyworker.model.person.PersonSearchResponse
import uk.gov.justice.digital.hmpps.keyworker.model.staff.AllocatableSearchRequest
import uk.gov.justice.digital.hmpps.keyworker.model.staff.AllocatableSearchResponse
import uk.gov.justice.digital.hmpps.keyworker.model.staff.StaffSearchRequest
import uk.gov.justice.digital.hmpps.keyworker.model.staff.StaffSearchResponse
import uk.gov.justice.digital.hmpps.keyworker.services.PersonSearch
import uk.gov.justice.digital.hmpps.keyworker.services.recordedevents.RecordedEventsSearch
import uk.gov.justice.digital.hmpps.keyworker.services.staff.StaffSearch

@RestController
@RequestMapping(value = ["/search"])
@PreAuthorize("hasRole('${Roles.ALLOCATIONS_UI}')")
class SearchController(
  private val staffSearch: StaffSearch,
  private val personSearch: PersonSearch,
  private val recordedEventSearch: RecordedEventsSearch,
) {
  @Operation(
    summary = "Search staff details from within a given prison.",
    description = "Search prison staff including filtering those that have and do not have the policy staff role."
  )
  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "200",
        description = "List of allocatable staff members returned"
      )
    ]
  )
  @StandardAoiErrorResponse
  @PolicyHeader
  @Tag(name = MANAGE_STAFF)
  @PostMapping("/prisons/{prisonCode}/staff-allocations")
  fun searchAllocatableStaff(
    @Parameter(required = true, example = "MDI", description = "The prison's identifier.")
    @PathVariable prisonCode: String,
    @RequestBody request: AllocatableSearchRequest,
    @Parameter(required = false, description = "Whether to include the staff member's stats in the response.")
    @RequestParam(required = false, defaultValue = "false") includeStats: Boolean,
  ): AllocatableSearchResponse = staffSearch.searchForAllocatableStaff(prisonCode, request, includeStats)

  @Operation(
    summary = "Retrieve staff details from within a given prison.",
    description = "Retrieve details for all staff members from within a given prison that match a query."
  )
  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "200",
        description = "List of staff members returned"
      )
    ]
  )
  @StandardAoiErrorResponse
  @PolicyHeader
  @Tag(name = MANAGE_STAFF)
  @PostMapping("/prisons/{prisonCode}/staff")
  fun searchStaff(
    @Parameter(required = true, example = "MDI", description = "The prison's identifier.")
    @PathVariable prisonCode: String,
    @RequestBody request: StaffSearchRequest,
  ): StaffSearchResponse = staffSearch.searchForStaff(prisonCode, request)

  @Operation(
    summary = "Retrieve a list of recorded events for a staff member.",
    description = "Retrieve a list of recorded events for a staff member."
  )
  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "200",
        description = "List of recorded events returned"
      )
    ]
  )
  @StandardAoiErrorResponse
  @PolicyHeader
  @Tag(name = MANAGE_STAFF)
  @PostMapping("/prisons/{prisonCode}/staff/{staffId}/recorded-events")
  fun searchStaffRecordedEvents(
    @Parameter(required = true, example = "MDI", description = "The prison's identifier.")
    @PathVariable prisonCode: String,
    @Parameter(required = true, example = "A12345", description = "The staff member's identifier.")
    @PathVariable staffId: Long,
    @RequestBody request: RecordedEventRequest,
  ): RecordedEventResponse = recordedEventSearch.searchForAuthor(prisonCode, staffId, request)

  @Operation(
     summary = "Retrieve a list of people for a prison.",
     description = "Retrieve a list of people for a prison that match a query."
   )
   @ApiResponses(
     value = [
       ApiResponse(
         responseCode = "200",
         description = "List of people returned"
       )
     ]
   )
  @StandardAoiErrorResponse
  @PolicyHeader
  @Tag(name = MANAGE_ALLOCATIONS)
  @PostMapping("/prisons/{prisonCode}/prisoners")
  fun searchPeople(
    @Parameter(required = true, example = "MDI", description = "The prison's identifier.")
    @PathVariable prisonCode: String,
    @RequestBody request: PersonSearchRequest,
  ): PersonSearchResponse = personSearch.findPeople(prisonCode, request)
}
