package uk.gov.justice.digital.hmpps.keyworker.services

import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.keyworker.dto.Allocation
import uk.gov.justice.digital.hmpps.keyworker.dto.CodedDescription
import uk.gov.justice.digital.hmpps.keyworker.dto.Keyworker
import uk.gov.justice.digital.hmpps.keyworker.dto.KeyworkerDetails
import uk.gov.justice.digital.hmpps.keyworker.dto.StaffLocationRoleDto
import uk.gov.justice.digital.hmpps.keyworker.integration.Prisoner
import uk.gov.justice.digital.hmpps.keyworker.integration.prisonersearch.PrisonerSearchClient
import uk.gov.justice.digital.hmpps.keyworker.model.KeyworkerStatus
import uk.gov.justice.digital.hmpps.keyworker.statistics.internal.KeyworkerAllocation
import uk.gov.justice.digital.hmpps.keyworker.statistics.internal.KeyworkerAllocationRepository
import uk.gov.justice.digital.hmpps.keyworker.statistics.internal.KeyworkerRepository
import uk.gov.justice.digital.hmpps.keyworker.statistics.internal.PrisonConfig
import uk.gov.justice.digital.hmpps.keyworker.statistics.internal.PrisonConfigRepository
import uk.gov.justice.digital.hmpps.keyworker.dto.Prisoner as Person

@Service
class GetKeyworkerDetails(
  private val prisonConfigRepository: PrisonConfigRepository,
  private val nomisService: NomisService,
  private val keyworkerRepository: KeyworkerRepository,
  private val allocationRepository: KeyworkerAllocationRepository,
  private val prisonerSearch: PrisonerSearchClient,
) {
  fun getFor(
    prisonCode: String,
    staffId: Long,
  ): KeyworkerDetails {
    val prisonConfig = lazy { prisonConfigRepository.findByIdOrNull(prisonCode) ?: PrisonConfig.default(prisonCode) }
    val keyworker =
      nomisService
        .getStaffKeyWorkerForPrison(prisonCode, staffId)
        .orElseGet { nomisService.getBasicKeyworkerDtoForStaffId(staffId) }
        .asKeyworker()

    val keyworkerInfo = keyworkerRepository.findAllWithAllocationCount(setOf(staffId)).firstOrNull()
    val allocations = allocationRepository.findActiveForPrisonStaff(prisonCode, staffId)
    val prisonerDetails =
      prisonerSearch
        .findPrisonerDetails(allocations.map { it.personIdentifier }.toSet())
        .filter { it.prisonId == prisonCode }
        .associateBy { it.prisonerNumber }
    val prisonName = prisonerDetails.values.firstOrNull()?.prisonName ?: ""

    return KeyworkerDetails(
      keyworker,
      (keyworkerInfo?.keyworker?.status ?: KeyworkerStatus.ACTIVE).codedDescription(),
      CodedDescription(prisonCode, prisonName),
      keyworkerInfo?.keyworker?.capacity ?: prisonConfig.value.capacityTier1,
      keyworkerInfo?.allocationCount ?: 0,
      CodedDescription(prisonName, prisonCode),
      allocations.mapNotNull { alloc -> prisonerDetails[alloc.personIdentifier]?.let { alloc.asAllocation(it) } },
    )
  }
}

fun StaffLocationRoleDto.asKeyworker() = Keyworker(staffId, firstName, lastName)

fun Prisoner.asPrisoner() = Person(prisonerNumber, firstName, lastName)

fun KeyworkerAllocation.asAllocation(prisoner: Prisoner) =
  Allocation(
    prisoner.asPrisoner(),
    prisoner.prisonName,
    prisoner.releaseDate,
    null,
  )
