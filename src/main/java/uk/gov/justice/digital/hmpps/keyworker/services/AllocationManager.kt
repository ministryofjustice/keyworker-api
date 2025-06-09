package uk.gov.justice.digital.hmpps.keyworker.services

import jakarta.validation.ValidationException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.keyworker.domain.PrisonConfigurationRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceData
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceDataDomain
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceDataDomain.ALLOCATION_REASON
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceDataDomain.DEALLOCATION_REASON
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceDataKey
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceDataRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.StaffAllocation
import uk.gov.justice.digital.hmpps.keyworker.domain.StaffAllocationRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.StaffConfigRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.getNonActiveStaff
import uk.gov.justice.digital.hmpps.keyworker.domain.of
import uk.gov.justice.digital.hmpps.keyworker.dto.PersonStaffAllocations
import uk.gov.justice.digital.hmpps.keyworker.dto.StaffWithRole
import uk.gov.justice.digital.hmpps.keyworker.integration.Prisoner
import uk.gov.justice.digital.hmpps.keyworker.integration.prisonersearch.PrisonerSearchClient
import uk.gov.justice.digital.hmpps.keyworker.model.AllocationReason.AUTO
import uk.gov.justice.digital.hmpps.keyworker.model.AllocationType
import uk.gov.justice.digital.hmpps.keyworker.model.DeallocationReason.OVERRIDE
import java.time.LocalDateTime

@Transactional
@Service
class AllocationManager(
  private val prisonConfigRepository: PrisonConfigurationRepository,
  private val prisonerSearch: PrisonerSearchClient,
  private val staffSearch: StaffSearch,
  private val staffConfigRepository: StaffConfigRepository,
  private val referenceDataRepository: ReferenceDataRepository,
  private val allocationRepository: StaffAllocationRepository,
) {
  fun manage(
    prisonCode: String,
    psa: PersonStaffAllocations,
  ) {
    check(!psa.isEmpty()) {
      "At least one allocation or deallocation must be provided"
    }
    prisonConfig(prisonCode)
    prisoners(prisonCode, psa.personIdentifiersToAllocate)
    activeStaff(prisonCode, psa.staffIdsToAllocate)
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
        .findAllByPersonIdentifierInAndActiveTrue(personIdentifiersToAllocate)
        .associateBy { it.personIdentifier }

    val newAllocations =
      allocations.mapNotNull {
        val existing = existingAllocations[it.personIdentifier]
        if (existing?.staffId == it.staffId) {
          null
        } else {
          existing?.deallocate(rdSupplier(DEALLOCATION_REASON, OVERRIDE.reasonCode))
          newAllocation(
            prisonCode,
            it.staffId,
            it.personIdentifier,
            rdSupplier(ALLOCATION_REASON, it.allocationReason),
          )
        }
      }
    if (newAllocations.isNotEmpty()) {
      allocationRepository.saveAll(newAllocations)
    }
  }

  private fun PersonStaffAllocations.deallocate(rdSupplier: (String) -> ReferenceData) {
    val existingAllocations =
      allocationRepository
        .findAllByPersonIdentifierInAndActiveTrue(personIdentifiersToDeallocate)
        .associateBy { it.personIdentifier }
    deallocations.forEach { psd ->
      existingAllocations[psd.personIdentifier]
        ?.takeIf { psd.staffId == it.staffId }
        ?.deallocate(rdSupplier(psd.deallocationReason))
    }
  }

  private fun prisonConfig(prisonCode: String) =
    prisonConfigRepository
      .findByCode(prisonCode)
      ?.takeIf { it.enabled }
      ?: throw IllegalArgumentException("Prison not enabled")

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

  private fun activeStaff(
    prisonCode: String,
    staffIds: Set<Long>,
  ): Map<Long, StaffWithRole> {
    if (staffIds.isEmpty()) {
      return emptyMap()
    }
    val staff = staffSearch.findStaff(prisonCode).associateBy { it.staffId }
    require(staff.keys.containsAll(staffIds)) {
      "A provided staff id is not allocatable for the provided prison"
    }
    val nonActiveIds =
      staffConfigRepository
        .getNonActiveStaff(staffIds)
        .map { it.staffId }
        .toSet()
    require(nonActiveIds.isEmpty()) {
      "A provided staff id is not an active staff member"
    }
    return staff
  }

  private fun PersonStaffAllocations.referenceData(): Map<ReferenceDataKey, ReferenceData> {
    val rdKeys = referenceDataKeys()
    val rd = referenceDataRepository.findAllByKeyIn(rdKeys).associateBy { it.key }
    check(rd.keys.containsAll(rd.keys))
    return rd
  }

  private fun PersonStaffAllocations.referenceDataKeys(): Set<ReferenceDataKey> {
    val allocationReasons =
      (allocations.map { ALLOCATION_REASON of it.allocationReason } + (ALLOCATION_REASON of AUTO.reasonCode)).toSet()
    val deallocationReasons =
      (deallocations.map { DEALLOCATION_REASON of it.deallocationReason } + (DEALLOCATION_REASON of OVERRIDE.reasonCode)).toSet()
    return allocationReasons + deallocationReasons
  }

  private fun allocatingUsername(): String =
    SecurityContextHolder
      .getContext()
      .authentication.name
      .takeIf { it.length <= 64 }
      ?: throw ValidationException("username for audit exceeds 64 characters")

  private fun newAllocation(
    prisonCode: String,
    staffId: Long,
    personIdentifier: String,
    allocationReason: ReferenceData,
  ) = StaffAllocation(
    personIdentifier = personIdentifier,
    prisonCode = prisonCode,
    staffId = staffId,
    assignedAt = LocalDateTime.now(),
    active = true,
    allocationReason = allocationReason,
    allocationType = AllocationType.valueOf(allocationReason.code),
    allocatedBy = allocatingUsername(),
    null,
    null,
    null,
  )
}
