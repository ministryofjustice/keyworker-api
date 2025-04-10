package uk.gov.justice.digital.hmpps.keyworker.services

import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.keyworker.domain.AllocationSummary
import uk.gov.justice.digital.hmpps.keyworker.domain.KeyworkerAllocationRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.PrisonConfigRepository
import uk.gov.justice.digital.hmpps.keyworker.dto.Keyworker
import uk.gov.justice.digital.hmpps.keyworker.dto.PersonSearchRequest
import uk.gov.justice.digital.hmpps.keyworker.dto.PersonSearchResponse
import uk.gov.justice.digital.hmpps.keyworker.dto.PrisonerSummary
import uk.gov.justice.digital.hmpps.keyworker.events.ComplexityOfNeedLevel.HIGH
import uk.gov.justice.digital.hmpps.keyworker.integration.Prisoner
import uk.gov.justice.digital.hmpps.keyworker.integration.prisonersearch.PrisonerSearchClient
import uk.gov.justice.digital.hmpps.keyworker.sar.StaffSummary
import uk.gov.justice.digital.hmpps.keyworker.sar.internal.StaffDetailProvider

@Service
class PersonSearch(
  private val prisonConfigRepository: PrisonConfigRepository,
  private val prisonerSearch: PrisonerSearchClient,
  private val complexityOfNeed: ComplexityOfNeedGateway,
  private val allocationRepository: KeyworkerAllocationRepository,
  private val staffDetailProvider: StaffDetailProvider,
) {
  fun findPeople(
    prisonCode: String,
    request: PersonSearchRequest,
  ): PersonSearchResponse {
    val prisonConfig = prisonConfigRepository.findByIdOrNull(prisonCode)
    val prisoners = prisonerSearch.findFilteredPrisoners(prisonCode, request)
    val complexNeedsMap =
      if (prisonConfig?.hasPrisonersWithHighComplexityNeeds == true) {
        complexityOfNeed
          .getOffendersWithMeasuredComplexityOfNeed(prisoners.personIdentifiers())
          .associateBy { it.offenderNo }
      } else {
        emptyMap()
      }

    val summaries =
      allocationRepository
        .summariesFor(prisonCode, prisoners.personIdentifiers())
        .associateBy { it.personIdentifier }
    val keyworkers =
      if (request.excludeActiveAllocations) {
        emptyMap()
      } else {
        staffDetailProvider
          .findStaffSummariesFromIds(summaries.values.mapNotNull { it.staffId }.toSet())
          .associateBy { it.staffId }
      }

    return PersonSearchResponse(
      prisoners.content.mapNotNull {
        it.summary(request.excludeActiveAllocations, complexNeedsMap::get, summaries::get, keyworkers::get)
      },
    )
  }

  private fun Prisoner.summary(
    excludeActive: Boolean,
    complexity: (String) -> ComplexOffender?,
    summary: (String) -> AllocationSummary?,
    staff: (Long) -> StaffSummary?,
  ): PrisonerSummary? {
    val summary = summary(prisonerNumber)
    return if (excludeActive && (summary?.activeCount ?: 0) > 0) {
      null
    } else {
      PrisonerSummary(
        prisonerNumber,
        firstName,
        lastName,
        cellLocation,
        complexity(prisonerNumber)?.level == HIGH,
        (summary?.totalCount ?: 0) > 0,
        summary?.staffId?.let { staff(it) }?.asKeyworker(),
      )
    }
  }

  private fun StaffSummary.asKeyworker(): Keyworker = Keyworker(staffId, firstName, lastName)
}
