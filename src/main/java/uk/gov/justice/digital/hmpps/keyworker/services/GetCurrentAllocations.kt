package uk.gov.justice.digital.hmpps.keyworker.services

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.keyworker.domain.AllocationRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.Policy
import uk.gov.justice.digital.hmpps.keyworker.domain.PolicyRepository
import uk.gov.justice.digital.hmpps.keyworker.dto.CodedDescription
import uk.gov.justice.digital.hmpps.keyworker.dto.CurrentAllocation
import uk.gov.justice.digital.hmpps.keyworker.dto.CurrentPersonStaffAllocation
import uk.gov.justice.digital.hmpps.keyworker.events.ComplexityOfNeedLevel
import uk.gov.justice.digital.hmpps.keyworker.integration.PrisonApiClient
import uk.gov.justice.digital.hmpps.keyworker.integration.prisonersearch.PrisonerSearchClient
import uk.gov.justice.digital.hmpps.keyworker.services.casenotes.CaseNoteRetriever
import uk.gov.justice.digital.hmpps.keyworker.services.casenotes.asRecordedEvents

@Service
class GetCurrentAllocations(
  private val prisonerSearch: PrisonerSearchClient,
  private val allocationRepository: AllocationRepository,
  private val caseNoteRetriever: CaseNoteRetriever,
  private val prisonApi: PrisonApiClient,
  private val prisonService: PrisonService,
  private val policyRepository: PolicyRepository,
) {
  fun currentFor(personIdentifier: String): CurrentPersonStaffAllocation {
    val person =
      prisonerSearch.findPrisonerDetails(setOf(personIdentifier)).firstOrNull()
        ?: return CurrentPersonStaffAllocation(personIdentifier, false, emptyList(), emptyList())
    return when (person.complexityOfNeedLevel) {
      ComplexityOfNeedLevel.HIGH -> CurrentPersonStaffAllocation(personIdentifier, true, emptyList(), emptyList())
      else -> {
        val allocations = allocationRepository.findCurrentAllocations(personIdentifier)
        val caseNotes = caseNoteRetriever.findMostRecentCaseNotes(personIdentifier)
        val prisons =
          prisonService
            .findPrisons((allocations.map { it.prisonCode } + caseNotes.map { it.prisonCode }).toSet())
            .associateBy { it.prisonId }
        val staff =
          prisonApi
            .findStaffSummariesFromIds((allocations.map { it.staffId } + caseNotes.map { it.staffId }).toSet())
            .associateBy { it.staffId }
        if (allocations.isEmpty()) {
          CurrentPersonStaffAllocation(personIdentifier, false, emptyList(), emptyList())
        } else {
          val policies = policyRepository.findAll().associateBy { it.code }
          return CurrentPersonStaffAllocation(
            personIdentifier,
            false,
            allocations.map {
              CurrentAllocation(
                policies[it.policy]!!.asCodedDescription(),
                prisons[it.prisonCode]!!.asCodedDescription(),
                staff[it.staffId]!!,
              )
            },
            caseNotes.asRecordedEvents({ requireNotNull(prisons[it]) }, { requireNotNull(staff[it]) }),
          )
        }
      }
    }
  }

  private fun Policy.asCodedDescription(): CodedDescription = CodedDescription(code, description)
}
