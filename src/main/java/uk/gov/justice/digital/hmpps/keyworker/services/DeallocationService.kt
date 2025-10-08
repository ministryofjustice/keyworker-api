package uk.gov.justice.digital.hmpps.keyworker.services

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.keyworker.domain.AllocationRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceDataDomain.DEALLOCATION_REASON
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceDataRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.getReferenceData
import uk.gov.justice.digital.hmpps.keyworker.domain.of
import uk.gov.justice.digital.hmpps.keyworker.dto.DeallocationReason

@Transactional
@Service
class DeallocationService(
  private val referenceDataRepository: ReferenceDataRepository,
  private val allocationRepository: AllocationRepository,
) {
  fun deallocateExpiredAllocations(
    prisonCode: String,
    personIdentifier: String,
    deallocationReason: DeallocationReason,
  ) {
    val reason = referenceDataRepository.getReferenceData(DEALLOCATION_REASON of deallocationReason.name)
    val allocations =
      allocationRepository.findActiveForAllPolicies(personIdentifier).mapNotNull {
        it.takeIf { it.prisonCode != prisonCode }?.apply { deallocate(reason) }
      }
    allocationRepository.saveAll(allocations)
  }
}
