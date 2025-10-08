package uk.gov.justice.digital.hmpps.keyworker.services

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationContext
import uk.gov.justice.digital.hmpps.keyworker.domain.Allocation
import uk.gov.justice.digital.hmpps.keyworker.domain.AllocationRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.AllocationType
import uk.gov.justice.digital.hmpps.keyworker.domain.PrisonConfiguration
import uk.gov.justice.digital.hmpps.keyworker.domain.PrisonConfigurationRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceData
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceDataDomain
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceDataDomain.ALLOCATION_REASON
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceDataDomain.DEALLOCATION_REASON
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceDataKey
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceDataRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.StaffConfigRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.StaffConfiguration
import uk.gov.justice.digital.hmpps.keyworker.domain.of
import uk.gov.justice.digital.hmpps.keyworker.dto.AllocationReason
import uk.gov.justice.digital.hmpps.keyworker.dto.DeallocationReason
import uk.gov.justice.digital.hmpps.keyworker.dto.person.PersonStaffAllocations
import uk.gov.justice.digital.hmpps.keyworker.dto.staff.StaffStatus
import uk.gov.justice.digital.hmpps.keyworker.integration.prisonersearch.Prisoner
import uk.gov.justice.digital.hmpps.keyworker.integration.prisonersearch.PrisonerSearchClient
import java.time.LocalDateTime

@Transactional
@Service
class AllocationManager(
  private val prisonConfigRepository: PrisonConfigurationRepository,
  private val prisonerSearch: PrisonerSearchClient,
  private val staffSearch: StaffSearch,
  private val staffConfigRepository: StaffConfigRepository,
  private val referenceDataRepository: ReferenceDataRepository,
  private val allocationRepository: AllocationRepository,
) {
  fun manage(
    prisonCode: String,
    psa: PersonStaffAllocations,
  ) {
    check(!psa.isEmpty()) {
      "At least one allocation or deallocation must be provided"
    }
    prisonConfig(prisonCode, psa.hasAutoAllocations())
    prisoners(prisonCode, psa.personIdentifiersToAllocate)
    psa.staffValidation(prisonCode)
    val rdMap = psa.referenceData()
    psa.deallocate { requireNotNull(rdMap[DEALLOCATION_REASON of it]) }
    psa.allocate(prisonCode) { domain, code -> requireNotNull(rdMap[domain of code]) }
  }

  private fun PersonStaffAllocations.allocate(
    prisonCode: String,
    rdSupplier: (ReferenceDataDomain, String) -> ReferenceData,
  ) {
    val existingAllocations =
      allocationRepository
        .findAllByPersonIdentifierInAndIsActiveTrue(personIdentifiersToAllocate)
        .associateBy { it.personIdentifier }

    val newAllocations =
      allocations.mapNotNull {
        val existing = existingAllocations[it.personIdentifier]
        if (existing?.staffId == it.staffId) {
          null
        } else {
          existing?.deallocate(rdSupplier(DEALLOCATION_REASON, DeallocationReason.OVERRIDE.name))
          newAllocation(
            prisonCode,
            it.staffId,
            it.personIdentifier,
            rdSupplier(ALLOCATION_REASON, it.allocationReason),
          )
        }
      }
    if (newAllocations.isNotEmpty()) {
      allocationRepository.deleteProvisionalFor(newAllocations.map { it.personIdentifier })
      allocationRepository.flush()
      allocationRepository.saveAll(newAllocations)
    }
  }

  private fun PersonStaffAllocations.deallocate(rdSupplier: (String) -> ReferenceData) {
    val existingAllocations =
      allocationRepository
        .findAllByPersonIdentifierInAndIsActiveTrue(personIdentifiersToDeallocate)
        .associateBy { it.personIdentifier }
    deallocations.forEach { psd ->
      existingAllocations[psd.personIdentifier]
        ?.takeIf { psd.staffId == it.staffId }
        ?.deallocate(rdSupplier(psd.deallocationReason))
    }
  }

  private fun prisonConfig(
    prisonCode: String,
    autoAllocations: Boolean,
  ): PrisonConfiguration {
    val prisonConfig =
      prisonConfigRepository
        .findByCode(prisonCode)
        ?.takeIf { it.enabled }
        ?: throw IllegalArgumentException("Prison not enabled")

    return prisonConfig.takeIf { !autoAllocations || it.allowAutoAllocation }
      ?: throw IllegalArgumentException("Prison does not allow auto-allocation")
  }

  private fun prisoners(
    prisonCode: String,
    personIdentifiers: Set<String>,
  ): Map<String, Prisoner> {
    if (personIdentifiers.isEmpty()) {
      return emptyMap()
    }
    val prisoners = prisonerSearch.findPrisonerDetails(personIdentifiers).associateBy { it.prisonerNumber }
    require(prisoners.values.all { it.prisonId == prisonCode }) {
      "A provided person identifier is not currently at the provided prison"
    }
    return prisoners
  }

  private fun PersonStaffAllocations.staffValidation(prisonCode: String) {
    val staffIds = allocations.map { it.staffId }.toSet()
    if (staffIds.isEmpty()) return

    val staff = staffSearch.findAllocatableStaff(prisonCode).map { it.staffMember.staffId }
    require(staff.containsAll(staffIds)) {
      "A provided staff id is not allocatable for the provided prison"
    }
    val staffConfig = staffConfigRepository.findAllByStaffIdIn(staffIds).associateBy { it.staffId }
    val fails = allocations.mapNotNull { allocation -> staffConfig[allocation.staffId].validate() }
    require(fails.isEmpty()) {
      "A provided staff id is not configured correctly for the allocation reason"
    }
  }

  private fun StaffConfiguration?.validate(): StaffConfiguration? =
    if (this == null || status.code == StaffStatus.ACTIVE.name) {
      null
    } else {
      this
    }

  private fun PersonStaffAllocations.referenceData(): Map<ReferenceDataKey, ReferenceData> {
    val rdKeys = referenceDataKeys()
    val rd = referenceDataRepository.findAllByKeyIn(rdKeys).associateBy { it.key }
    check(rd.keys.containsAll(rd.keys))
    return rd
  }

  private fun PersonStaffAllocations.referenceDataKeys(): Set<ReferenceDataKey> {
    val allocationReasons =
      (allocations.map { ALLOCATION_REASON of it.allocationReason } + (ALLOCATION_REASON of AllocationReason.AUTO.name)).toSet()
    val deallocationReasons =
      (deallocations.map { DEALLOCATION_REASON of it.deallocationReason } + (DEALLOCATION_REASON of DeallocationReason.OVERRIDE.name))
        .toSet()
    return allocationReasons + deallocationReasons
  }

  private fun newAllocation(
    prisonCode: String,
    staffId: Long,
    personIdentifier: String,
    allocationReason: ReferenceData,
  ) = Allocation(
    personIdentifier = personIdentifier,
    prisonCode = prisonCode,
    staffId = staffId,
    allocatedAt = LocalDateTime.now(),
    isActive = true,
    allocationReason = allocationReason,
    allocationType = AllocationType.valueOf(allocationReason.code),
    allocatedBy = AllocationContext.get().username,
    null,
    null,
    null,
  )
}
