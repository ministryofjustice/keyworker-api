package uk.gov.justice.digital.hmpps.keyworker.services

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.keyworker.domain.AllocationRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.AllocationSummary
import uk.gov.justice.digital.hmpps.keyworker.dto.PersonSearchRequest
import uk.gov.justice.digital.hmpps.keyworker.dto.PersonSearchResponse
import uk.gov.justice.digital.hmpps.keyworker.dto.PrisonerSummary
import uk.gov.justice.digital.hmpps.keyworker.dto.StaffAllocationCount
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
  private val allocationRepository: AllocationRepository,
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

    val staffAllocationCount =
      staff.values.map { it.staffId }.toSet().takeIf { it.isNotEmpty() }?.let { ids ->
        allocationRepository.findAllocationCountForStaff(ids).associate { it.staffId to it.count }
      } ?: emptyMap()

    return PersonSearchResponse(
      prisoners.content.mapNotNull {
        it.summary(request.excludeActiveAllocations, summaries::get, staff::get, staffAllocationCount::get)
      },
    )
  }

  private fun Prisoner.summary(
    excludeActive: Boolean,
    summary: (String) -> AllocationSummary?,
    staff: (Long) -> StaffSummary?,
    allocationCount: (Long) -> Int?,
  ): PrisonerSummary? {
    val summary = summary(prisonerNumber)
    return if (excludeActive && ((summary?.activeCount ?: 0) > 0 || complexityOfNeedLevel == HIGH)) {
      null
    } else {
      PrisonerSummary(
        prisonerNumber,
        firstName,
        lastName,
        cellLocation,
        complexityOfNeedLevel == HIGH,
        (summary?.totalCount ?: 0) > 0,
        summary?.staffId?.let { staff(it)?.withCount(allocationCount(it) ?: 0) },
        alerts.getRelevantAlertCodes(),
        alerts.getRemainingAlertCount(),
      )
    }
  }
}

private fun StaffSummary.withCount(count: Int) = StaffAllocationCount(staffId, firstName, lastName, count)
