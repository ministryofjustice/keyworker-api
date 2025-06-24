package uk.gov.justice.digital.hmpps.keyworker.services

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.keyworker.domain.AllocationSummary
import uk.gov.justice.digital.hmpps.keyworker.domain.StaffAllocationRepository
import uk.gov.justice.digital.hmpps.keyworker.dto.PersonSearchRequest
import uk.gov.justice.digital.hmpps.keyworker.dto.PersonSearchResponse
import uk.gov.justice.digital.hmpps.keyworker.dto.PrisonerSummaryWithAlertDetails
import uk.gov.justice.digital.hmpps.keyworker.dto.StaffSummary
import uk.gov.justice.digital.hmpps.keyworker.events.ComplexityOfNeedLevel.HIGH
import uk.gov.justice.digital.hmpps.keyworker.integration.PrisonApiClient
import uk.gov.justice.digital.hmpps.keyworker.integration.Prisoner
import uk.gov.justice.digital.hmpps.keyworker.integration.getRelevantAlertCodes
import uk.gov.justice.digital.hmpps.keyworker.integration.getRemainingAlertCount
import uk.gov.justice.digital.hmpps.keyworker.integration.prisonersearch.PrisonerSearchClient

@Service
class PersonSearch(
  private val prisonerSearch: PrisonerSearchClient,
  private val allocationRepository: StaffAllocationRepository,
  private val prisonApi: PrisonApiClient,
) {
  fun findPeople(
    prisonCode: String,
    request: PersonSearchRequest,
  ): PersonSearchResponse {
    val prisoners = prisonerSearch.findFilteredPrisoners(prisonCode, request)

    val summaries =
      allocationRepository
        .summariesFor(prisonCode, prisoners.personIdentifiers())
        .associateBy { it.personIdentifier }
    val staff =
      if (request.excludeActiveAllocations) {
        emptyMap()
      } else {
        prisonApi
          .findStaffSummariesFromIds(summaries.values.mapNotNull { it.staffId }.toSet())
          .associateBy { it.staffId }
      }

    return PersonSearchResponse(
      prisoners.content.mapNotNull {
        it.summary(request.excludeActiveAllocations, summaries::get, staff::get)
      },
    )
  }

  private fun Prisoner.summary(
    excludeActive: Boolean,
    summary: (String) -> AllocationSummary?,
    staff: (Long) -> StaffSummary?,
  ): PrisonerSummaryWithAlertDetails? {
    val summary = summary(prisonerNumber)
    return if (excludeActive && (summary?.activeCount ?: 0) > 0) {
      null
    } else {
      PrisonerSummaryWithAlertDetails(
        prisonerNumber,
        firstName,
        lastName,
        cellLocation,
        complexityOfNeedLevel == HIGH,
        (summary?.totalCount ?: 0) > 0,
        summary?.staffId?.let { staff(it) },
        alerts.getRelevantAlertCodes(),
        alerts.getRemainingAlertCount(),
      )
    }
  }
}
