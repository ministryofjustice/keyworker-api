package uk.gov.justice.digital.hmpps.keyworker.services

import jakarta.validation.ValidationException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.keyworker.domain.KeyworkerAllocation
import uk.gov.justice.digital.hmpps.keyworker.domain.KeyworkerAllocationRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.KeyworkerRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.PrisonConfigRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceData
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceDataDomain
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceDataDomain.ALLOCATION_REASON
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceDataDomain.DEALLOCATION_REASON
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceDataKey
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceDataRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.getNonActiveKeyworkers
import uk.gov.justice.digital.hmpps.keyworker.domain.of
import uk.gov.justice.digital.hmpps.keyworker.dto.PagingAndSortingDto.activeStaffKeyWorkersPagingAndSorting
import uk.gov.justice.digital.hmpps.keyworker.dto.PersonStaffAllocations
import uk.gov.justice.digital.hmpps.keyworker.dto.StaffLocationRoleDto
import uk.gov.justice.digital.hmpps.keyworker.integration.Prisoner
import uk.gov.justice.digital.hmpps.keyworker.integration.prisonersearch.PrisonerSearchClient
import uk.gov.justice.digital.hmpps.keyworker.model.AllocationType
import uk.gov.justice.digital.hmpps.keyworker.model.DeallocationReason.OVERRIDE
import uk.gov.justice.digital.hmpps.keyworker.security.AuthAwareAuthenticationToken
import java.time.LocalDateTime
import java.util.Optional

@Transactional
@Service
class KeyworkerAllocationManager(
  private val prisonConfigRepository: PrisonConfigRepository,
  private val prisonerSearch: PrisonerSearchClient,
  private val nomisService: NomisService,
  private val keyworkerRepository: KeyworkerRepository,
  private val referenceDataRepository: ReferenceDataRepository,
  private val allocationRepository: KeyworkerAllocationRepository,
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
    activeKeyworkers(prisonCode, psa.staffIdsToAllocate)
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
          newKeyworkerAllocation(
            prisonCode,
            it.staffId,
            it.personIdentifier,
            rdSupplier(ALLOCATION_REASON, it.allocationReason),
          )
        }
      }
    allocationRepository.saveAll(newAllocations)
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
      .findByIdOrNull(prisonCode)
      ?.takeIf { it.migrated }
      ?: throw IllegalArgumentException("Prison not enabled for keyworker service")

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

  private fun activeKeyworkers(
    prisonCode: String,
    staffIds: Set<Long>,
  ): Map<Long, StaffLocationRoleDto> {
    if (staffIds.isEmpty()) {
      return emptyMap()
    }
    return nomisService
      .getActiveStaffKeyWorkersForPrison(
        prisonCode,
        Optional.empty(),
        activeStaffKeyWorkersPagingAndSorting(),
        true,
      )?.body
      ?.let { nomisKeyworkers ->
        val nomisKeyworkerMap = nomisKeyworkers.associateBy { it.staffId }
        require(nomisKeyworkerMap.keys.containsAll(staffIds)) {
          "A provided staff id is not a keyworker for the provided prison"
        }
        val nonActiveIds =
          keyworkerRepository
            .getNonActiveKeyworkers(staffIds)
            .map { it.staffId }
            .toSet()
        require(nonActiveIds.isEmpty()) {
          "A provided staff id is not an active keyworker"
        }
        nomisKeyworkerMap
      } ?: throw IllegalStateException("No active keyworkers found for the provided prison")
  }

  private fun PersonStaffAllocations.referenceData(): Map<ReferenceDataKey, ReferenceData> {
    val rdKeys = referenceDataKeys()
    val rd = referenceDataRepository.findAllByKeyIn(rdKeys).associateBy { it.key }
    check(rd.keys.containsAll(rd.keys))
    return rd
  }

  private fun PersonStaffAllocations.referenceDataKeys(): Set<ReferenceDataKey> {
    val allocationReasons = allocations.map { ALLOCATION_REASON of it.allocationReason }.toSet()
    val deallocationReasons =
      (deallocations.map { DEALLOCATION_REASON of it.deallocationReason } + (DEALLOCATION_REASON of OVERRIDE.reasonCode)).toSet()
    return allocationReasons + deallocationReasons
  }

  private fun authentication(): AuthAwareAuthenticationToken =
    SecurityContextHolder.getContext().authentication as AuthAwareAuthenticationToken?
      ?: throw AccessDeniedException("User is not authenticated")

  private fun AuthAwareAuthenticationToken.username(): String =
    name.takeIf { it.length <= 64 }
      ?: throw ValidationException("username for audit exceeds 64 characters")

  private fun newKeyworkerAllocation(
    prisonCode: String,
    staffId: Long,
    personIdentifier: String,
    allocationReason: ReferenceData,
  ) = KeyworkerAllocation(
    personIdentifier = personIdentifier,
    prisonCode = prisonCode,
    staffId = staffId,
    assignedAt = LocalDateTime.now(),
    active = true,
    allocationReason = allocationReason,
    allocationType = AllocationType.valueOf(allocationReason.code),
    allocatedBy = authentication().username(),
    null,
    null,
    null,
  )
}
