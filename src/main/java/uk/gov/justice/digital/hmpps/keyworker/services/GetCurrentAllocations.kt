package uk.gov.justice.digital.hmpps.keyworker.services

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationPolicy
import uk.gov.justice.digital.hmpps.keyworker.domain.Policy
import uk.gov.justice.digital.hmpps.keyworker.domain.PolicyRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.StaffAllocationRepository
import uk.gov.justice.digital.hmpps.keyworker.dto.CodedDescription
import uk.gov.justice.digital.hmpps.keyworker.dto.CurrentAllocation
import uk.gov.justice.digital.hmpps.keyworker.dto.CurrentPersonStaffAllocation
import uk.gov.justice.digital.hmpps.keyworker.dto.RecordedEvent
import uk.gov.justice.digital.hmpps.keyworker.events.ComplexityOfNeedLevel
import uk.gov.justice.digital.hmpps.keyworker.integration.PrisonApiClient
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNotesApiClient
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.UsageByPersonIdentifierRequest
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.UsageByPersonIdentifierRequest.Companion.personalOfficerTypes
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.UsageByPersonIdentifierRequest.Companion.sessionTypes
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.summary
import uk.gov.justice.digital.hmpps.keyworker.integration.prisonersearch.PrisonerSearchClient
import java.time.LocalDate.now

@Service
class GetCurrentAllocations(
  private val prisonerSearch: PrisonerSearchClient,
  private val allocationRepository: StaffAllocationRepository,
  private val prisonApi: PrisonApiClient,
  private val prisonService: PrisonService,
  private val caseNotesApiClient: CaseNotesApiClient,
  private val policyRepository: PolicyRepository,
) {
  fun currentFor(personIdentifier: String): CurrentPersonStaffAllocation {
    val person = prisonerSearch.findPrisonerDetails(setOf(personIdentifier)).firstOrNull()
    if (person == null) {
      return CurrentPersonStaffAllocation(personIdentifier, false, emptyList(), emptyList())
    }
    return when (person.complexityOfNeedLevel) {
      ComplexityOfNeedLevel.HIGH -> CurrentPersonStaffAllocation(personIdentifier, true, emptyList(), emptyList())
      else -> {
        val allocations = allocationRepository.findCurrentAllocations(personIdentifier)
        val prisons = prisonService.findPrisons(allocations.map { it.prisonCode }.toSet()).associateBy { it.prisonId }
        val recordedEvents =
          allocations.mapNotNull {
            val policy = AllocationPolicy.of(it.policy)!!
            val recordedEventRequest: UsageByPersonIdentifierRequest =
              when (policy) {
                AllocationPolicy.KEY_WORKER ->
                  sessionTypes(
                    it.prisonCode,
                    setOf(personIdentifier),
                    from = now().minusMonths(38),
                    to = now(),
                  )

                AllocationPolicy.PERSONAL_OFFICER ->
                  personalOfficerTypes(
                    it.prisonCode,
                    setOf(personIdentifier),
                    from = now().atStartOfDay().minusMonths(38),
                    to = now().atStartOfDay(),
                  )
              }
            caseNotesApiClient
              .getUsageByPersonIdentifier(recordedEventRequest)
              .summary()
              .getRecordedFor(policy, personIdentifier, requireNotNull(prisons[it.prisonCode]))
          }
        val staff =
          prisonApi.findStaffSummariesFromIds(allocations.map { it.staffId }.toSet()).associateBy { it.staffId }
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
            recordedEvents.map { RecordedEvent(it.prison.asCodedDescription(), it.type, it.lastOccurredAt) },
          )
        }
      }
    }
  }

  private fun Policy.asCodedDescription(): CodedDescription = CodedDescription(code, description)
}
