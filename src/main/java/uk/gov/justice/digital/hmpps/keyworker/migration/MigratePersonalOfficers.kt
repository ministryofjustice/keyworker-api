package uk.gov.justice.digital.hmpps.keyworker.migration

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.scheduling.annotation.Async
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
import uk.gov.justice.digital.hmpps.keyworker.domain.StaffRole
import uk.gov.justice.digital.hmpps.keyworker.domain.StaffRoleRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.of
import uk.gov.justice.digital.hmpps.keyworker.integration.PrisonApiClient
import uk.gov.justice.digital.hmpps.keyworker.integration.prisonersearch.PrisonerSearchClient
import uk.gov.justice.digital.hmpps.keyworker.model.AllocationReason
import uk.gov.justice.digital.hmpps.keyworker.model.AllocationType
import uk.gov.justice.digital.hmpps.keyworker.model.DeallocationReason
import java.math.BigDecimal

@Service
class MigratePersonalOfficers(
  private val ach: AllocationContextHolder,
  private val transactionTemplate: TransactionTemplate,
  private val prisonApi: PrisonApiClient,
  private val prisonerSearch: PrisonerSearchClient,
  private val referenceDataRepository: ReferenceDataRepository,
  private val allocationRepository: AllocationRepository,
  private val staffRoleRepository: StaffRoleRepository,
  private val telemetryClient: TelemetryClient,
) {
  @Async
  fun migrate(prisonCode: String) =
    try {
      val historicAllocations =
        prisonApi
          .getPersonalOfficerHistory(prisonCode)
          .groupBy { it.offenderNo }
      val currentResidentIds = prisonerSearch.findAllPrisoners(prisonCode).personIdentifiers()
      val expiredResidentIds = historicAllocations.keys - currentResidentIds
      val movementMap = prisonApi.getPersonMovements(expiredResidentIds)

      ach.setContext(AllocationContext.get().copy(policy = AllocationPolicy.PERSONAL_OFFICER))
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
                  ReferenceDataDomain.STAFF_POSITION of "PRO",
                  ReferenceDataDomain.STAFF_SCHEDULE_TYPE of "FT",
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

          val staffAllocations = allocations.filter { it.isActive }.groupBy { it.staffId }
          val staffRoles =
            staffAllocations.keys.map {
              StaffRole(
                rd[ReferenceDataDomain.STAFF_POSITION to "PRO"]!!,
                rd[ReferenceDataDomain.STAFF_SCHEDULE_TYPE to "FT"]!!,
                BigDecimal(35),
                staffAllocations[it]!!.minOf { a -> a.allocatedAt }.toLocalDate(),
                null,
                prisonCode,
                it,
              )
            }

          allocationRepository.saveAll(allocations) to staffRoleRepository.saveAll(staffRoles)
        }!!

      telemetryClient.trackEvent(
        "InitialMigrationComplete",
        mapOf(
          "policy" to AllocationPolicy.PERSONAL_OFFICER.name,
          "totalMigrationCount" to results.first.size.toString(),
          "activeCount" to results.first.count { it.isActive }.toString(),
          "deallocatedCount" to results.first.count { !it.isActive }.toString(),
          "notResidentCount" to
            results.first.count { it.deallocationReason?.code == DeallocationReason.MISSING.reasonCode }.toString(),
          "staffRolesAssigned" to results.second.size.toString(),
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
