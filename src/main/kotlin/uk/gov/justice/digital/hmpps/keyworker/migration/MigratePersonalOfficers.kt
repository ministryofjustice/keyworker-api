package uk.gov.justice.digital.hmpps.keyworker.migration

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationContext
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationPolicy
import uk.gov.justice.digital.hmpps.keyworker.config.set
import uk.gov.justice.digital.hmpps.keyworker.domain.Allocation
import uk.gov.justice.digital.hmpps.keyworker.domain.AllocationRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceData
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceDataDomain
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceDataRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.StaffRole
import uk.gov.justice.digital.hmpps.keyworker.domain.StaffRoleRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.of
import uk.gov.justice.digital.hmpps.keyworker.integration.prisonapi.PrisonApiClient
import uk.gov.justice.digital.hmpps.keyworker.integration.prisonersearch.PrisonerSearchClient
import uk.gov.justice.digital.hmpps.keyworker.model.AllocationReason
import uk.gov.justice.digital.hmpps.keyworker.model.DeallocationReason
import java.math.BigDecimal
import java.time.Duration
import java.time.LocalDateTime

@Service
class MigratePersonalOfficers(
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
      val nonResidentIds = historicAllocations.keys - currentResidentIds

      AllocationContext.get().copy(policy = AllocationPolicy.PERSONAL_OFFICER).set()
      val results =
        transactionTemplate.execute {
          val rd =
            referenceDataRepository
              .findAllByKeyIn(
                setOf(
                  ReferenceDataDomain.ALLOCATION_REASON of AllocationReason.MANUAL.name,
                  ReferenceDataDomain.DEALLOCATION_REASON of DeallocationReason.OVERRIDE.name,
                  ReferenceDataDomain.DEALLOCATION_REASON of DeallocationReason.NO_LONGER_IN_PRISON.name,
                  ReferenceDataDomain.DEALLOCATION_REASON of DeallocationReason.PRISON_USES_KEY_WORK.name,
                  ReferenceDataDomain.DEALLOCATION_REASON of DeallocationReason.MIGRATION.name,
                  ReferenceDataDomain.DEALLOCATION_REASON of DeallocationReason.TRANSFER.name,
                  ReferenceDataDomain.DEALLOCATION_REASON of DeallocationReason.RELEASED.name,
                  ReferenceDataDomain.STAFF_POSITION of "PRO",
                  ReferenceDataDomain.STAFF_SCHEDULE_TYPE of "FT",
                ),
              ).associateBy { (it.domain to it.code) }

          val allocReason = { code: String -> requireNotNull(rd[ReferenceDataDomain.ALLOCATION_REASON to code]) }
          val deallocReason = { code: String -> rd[ReferenceDataDomain.DEALLOCATION_REASON to code] }

          val allocations: List<Allocation> =
            Flux
              .fromIterable(historicAllocations.entries)
              .flatMap({ e ->
                if (e.key in currentResidentIds) {
                  Mono.just(PoAllocationHistory(e.value, deallocatingMovement(prisonCode)))
                } else {
                  prisonApi.getPersonMovements(e.key).map { ms ->
                    PoAllocationHistory(
                      e.value,
                      RelevantMovement(
                        prisonCode,
                        ms.filter { m -> m.createdAt.isAfter(e.value.maxOf { it.assigned }) },
                      ),
                    )
                  }
                }
              }, 20)
              .flatMap { Flux.fromIterable(it.allocations).map { a -> a.asAllocation(allocReason, deallocReason) } }
              .collectList()
              .block()!!

          val staffAllocations = allocations.filter { it.isActive }.groupBy { it.staffId }
          val staffRoles =
            staffRoleRepository
              .saveAll(
                staffAllocations.keys.map {
                  StaffRole(
                    rd[ReferenceDataDomain.STAFF_POSITION to "PRO"]!!,
                    rd[ReferenceDataDomain.STAFF_SCHEDULE_TYPE to "FT"]!!,
                    BigDecimal(35),
                    requireNotNull(staffAllocations[it]).minOf { a -> a.allocatedAt }.toLocalDate(),
                    null,
                    prisonCode,
                    it,
                  )
                },
              ).toList()

          allocations
            .chunked(1000)
            .map {
              allocationRepository.saveAll(it)
              allocationRepository.flush()
              allocationRepository.clear()
              it
            }.flatten()
            .toList() to staffRoles
        }!!

      telemetryClient.trackEvent(
        "InitialMigrationComplete",
        mapOf(
          "prisonCode" to prisonCode,
          "timeTaken" to Duration.between(AllocationContext.get().requestAt, LocalDateTime.now()).toString(),
          "policy" to AllocationPolicy.PERSONAL_OFFICER.name,
          "totalMigrationCount" to results.first.size.toString(),
          "activeCount" to results.first.count { it.isActive }.toString(),
          "deallocatedCount" to results.first.count { !it.isActive }.toString(),
          "notResidentCount" to nonResidentIds.size.toString(),
          "staffRolesAssigned" to results.second.size.toString(),
        ),
        null,
      )
    } finally {
      AllocationContext.clear()
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
    allocationReason(AllocationReason.MANUAL.name),
    userId,
    deallocatedAt,
    deallocationReasonCode?.let { deallocationReason(it) },
    deallocatedBy,
    policy = AllocationPolicy.PERSONAL_OFFICER.name,
  )

  companion object {
    val MIGRATING_PRISON_CODES =
      setOf(
        "EHI",
        "GNI",
        "HBI",
        "HDI",
        "HVI",
        "KMI",
        "KVI",
        "LYI",
        "NSI",
        "SPI",
        "SUI",
        "TCI",
        "UPI",
      )
    const val FORD_PRISON_CODE = "FDI"

    fun deallocatingMovement(prisonCode: String): RelevantMovement? {
      val movement =
        when (prisonCode) {
          in MIGRATING_PRISON_CODES -> null
          FORD_PRISON_CODE -> DeallocateAll(prisonCode, DeallocationReason.MIGRATION)
          else -> DeallocateAll(prisonCode)
        }
      return movement?.let { RelevantMovement(prisonCode, listOf(movement)) }
    }
  }
}
