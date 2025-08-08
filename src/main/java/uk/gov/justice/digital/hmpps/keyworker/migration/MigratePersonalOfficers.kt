package uk.gov.justice.digital.hmpps.keyworker.migration

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationContext
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationContextHolder
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationPolicy
import uk.gov.justice.digital.hmpps.keyworker.domain.Allocation
import uk.gov.justice.digital.hmpps.keyworker.domain.AllocationRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceData
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceDataDomain
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceDataRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.of
import uk.gov.justice.digital.hmpps.keyworker.integration.PrisonApiClient
import uk.gov.justice.digital.hmpps.keyworker.integration.events.HmppsDomainEvent
import uk.gov.justice.digital.hmpps.keyworker.integration.events.PersonalOfficerMigrationInformation
import uk.gov.justice.digital.hmpps.keyworker.integration.prisonersearch.PrisonerSearchClient
import uk.gov.justice.digital.hmpps.keyworker.model.AllocationReason
import uk.gov.justice.digital.hmpps.keyworker.model.AllocationType
import uk.gov.justice.digital.hmpps.keyworker.model.DeallocationReason

@Service
class MigratePersonalOfficers(
  private val ach: AllocationContextHolder,
  private val transactionTemplate: TransactionTemplate,
  private val prisonApi: PrisonApiClient,
  private val prisonerSearch: PrisonerSearchClient,
  private val referenceDataRepository: ReferenceDataRepository,
  private val allocationRepository: AllocationRepository,
  private val telemetryClient: TelemetryClient,
) {
  fun handle(event: HmppsDomainEvent<PersonalOfficerMigrationInformation>) =
    try {
      val prisonCode = event.additionalInformation.prisonCode
      val historicAllocations =
        prisonApi
          .getPersonalOfficerHistory(prisonCode)
          .groupBy { it.offenderNo }
      val currentResidentIds = prisonerSearch.findAllPrisoners(prisonCode).personIdentifiers()
      val expiredResidentIds = historicAllocations.keys - currentResidentIds
      val movementMap =
        expiredResidentIds
          .flatMap { pi ->
            historicAllocations[pi]
              ?.maxByOrNull { it.assigned }
              ?.let { prisonApi.getPersonMovements(pi, it.assigned.toLocalDate()) } ?: emptyList()
          }.groupBy { it.offenderNo }

      ach.setContext(AllocationContext.Companion.get().copy(policy = AllocationPolicy.PERSONAL_OFFICER))
      val results =
        transactionTemplate.execute {
          val rd =
            referenceDataRepository
              .findAllByKeyIn(
                setOf(
                  ReferenceDataDomain.ALLOCATION_REASON of AllocationReason.MANUAL.reasonCode,
                  ReferenceDataDomain.DEALLOCATION_REASON of DeallocationReason.OVERRIDE.reasonCode,
                  ReferenceDataDomain.DEALLOCATION_REASON of DeallocationReason.MISSING.reasonCode,
                  ReferenceDataDomain.DEALLOCATION_REASON of DeallocationReason.TRANSFER.reasonCode,
                  ReferenceDataDomain.DEALLOCATION_REASON of DeallocationReason.RELEASED.reasonCode,
                ),
              ).associateBy { (it.domain to it.code) }

          val allocReason = { code: String -> requireNotNull(rd[ReferenceDataDomain.ALLOCATION_REASON to code]) }
          val deallocReason = { code: String -> rd[ReferenceDataDomain.DEALLOCATION_REASON to code] }

          val allocations =
            historicAllocations
              .map {
                PoAllocationHistory(
                  it.value,
                  if (it.key in currentResidentIds) {
                    null
                  } else {
                    MovementHistory(prisonCode, movementMap[it.key] ?: emptyList())
                  },
                )
              }.flatMap { it.allocations.map { a -> a.asAllocation(allocReason, deallocReason) } }

          allocationRepository.saveAll(allocations)
        }!!

      telemetryClient.trackEvent(
        "InitialMigrationComplete",
        mapOf(
          "policy" to AllocationPolicy.PERSONAL_OFFICER.name,
          "totalMigrationCount" to results.size.toString(),
          "activeCount" to results.count { it.isActive }.toString(),
          "deallocatedCount" to results.count { !it.isActive }.toString(),
          "notResidentCount" to
            results
              .count { it.deallocationReason?.code == DeallocationReason.MISSING.reasonCode }
              .toString(),
        ),
        null,
      )
    } finally {
      ach.clearContext()
    }

  private fun PoHistoricAllocation.asAllocation(
    allocationReason: (String) -> ReferenceData,
    deallocationReason: (String) -> ReferenceData?,
  ) = Allocation(
    offenderNo,
    agencyId,
    staffId,
    assigned,
    deallocatedAt == null,
    allocationReason(AllocationReason.MANUAL.reasonCode),
    AllocationType.MANUAL,
    userId,
    deallocatedAt,
    deallocationReasonCode?.let { deallocationReason(it) },
    deallocatedBy,
  )
}
