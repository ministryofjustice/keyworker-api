package uk.gov.justice.digital.hmpps.keyworker.services

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationPolicy
import uk.gov.justice.digital.hmpps.keyworker.domain.AllocationRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.LatestRecordedEvent
import uk.gov.justice.digital.hmpps.keyworker.domain.Policy
import uk.gov.justice.digital.hmpps.keyworker.domain.PolicyRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.PrisonConfigurationRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.RecordedEventRepository
import uk.gov.justice.digital.hmpps.keyworker.integration.events.offender.ComplexityOfNeedLevel
import uk.gov.justice.digital.hmpps.keyworker.integration.prisonapi.PrisonApiClient
import uk.gov.justice.digital.hmpps.keyworker.integration.prisonersearch.PrisonerSearchClient
import uk.gov.justice.digital.hmpps.keyworker.integration.prisonregister.asCodedDescription
import uk.gov.justice.digital.hmpps.keyworker.model.CodedDescription
import uk.gov.justice.digital.hmpps.keyworker.model.person.Author
import uk.gov.justice.digital.hmpps.keyworker.model.person.CurrentAllocation
import uk.gov.justice.digital.hmpps.keyworker.model.person.CurrentPersonStaffAllocation
import uk.gov.justice.digital.hmpps.keyworker.model.person.CurrentStaffSummary
import uk.gov.justice.digital.hmpps.keyworker.model.person.RecordedEvent
import uk.gov.justice.digital.hmpps.keyworker.model.prison.PolicyEnabled
import uk.gov.justice.digital.hmpps.keyworker.model.staff.RecordedEventType
import uk.gov.justice.digital.hmpps.keyworker.model.staff.StaffSummary
import uk.gov.justice.digital.hmpps.keyworker.model.staff.orDefault

@Service
class GetCurrentAllocations(
  private val prisonerSearch: PrisonerSearchClient,
  private val prisonConfig: PrisonConfigurationRepository,
  private val allocationRepository: AllocationRepository,
  private val recordedEventRepository: RecordedEventRepository,
  private val prisonApi: PrisonApiClient,
  private val prisonService: PrisonService,
  private val policyRepository: PolicyRepository,
) {
  fun currentFor(
    personIdentifier: String,
    includeContactDetails: Boolean,
  ): CurrentPersonStaffAllocation {
    val person =
      prisonerSearch.findPrisonerDetails(setOf(personIdentifier)).firstOrNull()
        ?: return CurrentPersonStaffAllocation(personIdentifier)
    val prisonPolicies = prisonConfig.findEnabledPrisonPolicies(person.lastPrisonId)
    return when (person.complexityOfNeedLevel) {
      ComplexityOfNeedLevel.HIGH ->
        CurrentPersonStaffAllocation(
          personIdentifier,
          hasHighComplexityOfNeeds = true,
          policies = prisonPolicies.enabled(),
        )

      else -> {
        val allocations = allocationRepository.findCurrentAllocations(personIdentifier, prisonPolicies)
        val recordedEvents = recordedEventRepository.findLatestRecordedEvents(personIdentifier, prisonPolicies)
        val prisons =
          prisonService
            .findPrisons((allocations.map { it.prisonCode } + recordedEvents.map { it.prisonCode }).toSet())
            .associateBy { it.prisonId }
        val allocationStaffIds = allocations.map { it.staffId }.toSet()
        val recordedEventStaffIds = recordedEvents.map { it.staffId }.toSet()
        val allStaffIds = allocationStaffIds + recordedEventStaffIds
        val staffEmailIds = if (includeContactDetails) allocationStaffIds else emptySet()
        val staff =
          prisonApi
            .findStaffSummariesFromIds(allStaffIds)
            .map { s -> s.associateBy { it.staffId } }
            .zipWith(prisonApi.getStaffEmails(staffEmailIds).map { it.toMap() })
            .map {
              val summaries = it.t1
              val emails = it.t2
              allStaffIds.map { id -> summary(id, { summaries[id] }, { emails[id] }) }
            }.block()!!
            .associateBy { it.staffId }
        val mappedEvents = recordedEvents.asRecordedEvents({ prisons[it].orDefault(it) }, { requireNotNull(staff[it]) })
        if (allocations.isEmpty()) {
          CurrentPersonStaffAllocation(
            personIdentifier,
            latestRecordedEvents = mappedEvents,
            policies = prisonPolicies.enabled(),
          )
        } else {
          val policies = policyRepository.findAll().associateBy { AllocationPolicy.of(it.code) }
          CurrentPersonStaffAllocation(
            personIdentifier,
            false,
            allocations.map {
              CurrentAllocation(
                checkNotNull(policies[it.policy]).asCodedDescription(),
                prisons[it.prisonCode].orDefault(it.prisonCode).asCodedDescription(),
                requireNotNull(staff[it.staffId]),
              )
            },
            mappedEvents,
            prisonPolicies.enabled(),
          )
        }
      }
    }
  }

  private fun summary(
    staffId: Long,
    summary: (Long) -> StaffSummary?,
    emails: (Long) -> Set<String>?,
  ): CurrentStaffSummary {
    val staffSummary = summary(staffId).orDefault(staffId)
    return CurrentStaffSummary(staffId, staffSummary.firstName, staffSummary.lastName, emails(staffId) ?: emptySet())
  }

  private fun Policy.asCodedDescription(): CodedDescription = CodedDescription(code, description)
}

private fun Set<String>.enabled() =
  AllocationPolicy.entries.map {
    PolicyEnabled(it, it.name in this)
  }

private fun List<LatestRecordedEvent>.asRecordedEvents(
  prisons: (String) -> Prison,
  staff: (Long) -> CurrentStaffSummary,
): List<RecordedEvent> =
  map { lre ->
    RecordedEvent(
      prisons(lre.prisonCode).asCodedDescription(),
      RecordedEventType.valueOf(lre.typeCode),
      lre.occurredAt,
      requireNotNull(AllocationPolicy.of(lre.policyCode)),
      staff(lre.staffId).asAuthor(lre.username),
    )
  }

private fun CurrentStaffSummary.asAuthor(username: String) = Author(staffId, firstName, lastName, username)
