package uk.gov.justice.digital.hmpps.keyworker.services

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationPolicy
import uk.gov.justice.digital.hmpps.keyworker.domain.AllocationRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.Policy
import uk.gov.justice.digital.hmpps.keyworker.domain.PolicyRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.PrisonConfigurationRepository
import uk.gov.justice.digital.hmpps.keyworker.dto.CodedDescription
import uk.gov.justice.digital.hmpps.keyworker.dto.CurrentAllocation
import uk.gov.justice.digital.hmpps.keyworker.dto.CurrentPersonStaffAllocation
import uk.gov.justice.digital.hmpps.keyworker.dto.CurrentStaffSummary
import uk.gov.justice.digital.hmpps.keyworker.dto.PolicyEnabled
import uk.gov.justice.digital.hmpps.keyworker.dto.StaffSummary
import uk.gov.justice.digital.hmpps.keyworker.dto.orDefault
import uk.gov.justice.digital.hmpps.keyworker.events.ComplexityOfNeedLevel
import uk.gov.justice.digital.hmpps.keyworker.integration.PrisonApiClient
import uk.gov.justice.digital.hmpps.keyworker.integration.prisonersearch.PrisonerSearchClient
import uk.gov.justice.digital.hmpps.keyworker.services.recordedevents.RecordedEventRetriever
import uk.gov.justice.digital.hmpps.keyworker.services.recordedevents.asRecordedEvents

@Service
class GetCurrentAllocations(
  private val prisonerSearch: PrisonerSearchClient,
  private val prisonConfig: PrisonConfigurationRepository,
  private val allocationRepository: AllocationRepository,
  private val recordedEventRetriever: RecordedEventRetriever,
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
        val recordedEvents = recordedEventRetriever.findMostRecentRecordedEvents(personIdentifier, prisonPolicies)
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
        if (allocations.isEmpty()) {
          CurrentPersonStaffAllocation(
            personIdentifier,
            latestRecordedEvents =
              recordedEvents.asRecordedEvents(
                { prisons[it].orDefault(it) },
                { requireNotNull(staff[it]) },
              ),
            policies = prisonPolicies.enabled(),
          )
        } else {
          val policies = policyRepository.findAll().associateBy { it.code }
          return CurrentPersonStaffAllocation(
            personIdentifier,
            false,
            allocations.map {
              CurrentAllocation(
                policies[it.policy]!!.asCodedDescription(),
                prisons[it.prisonCode].orDefault(it.prisonCode).asCodedDescription(),
                requireNotNull(staff[it.staffId]),
              )
            },
            recordedEvents.asRecordedEvents({ prisons[it].orDefault(it) }, { requireNotNull(staff[it]) }),
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

fun Set<String>.enabled() =
  AllocationPolicy.entries.map {
    PolicyEnabled(it, it.name in this)
  }
