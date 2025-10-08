package uk.gov.justice.digital.hmpps.keyworker.services

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.keyworker.domain.AllocationRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceDataDomain.DEALLOCATION_REASON
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceDataKey
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceDataRepository
import uk.gov.justice.digital.hmpps.keyworker.dto.DeallocationReason
import uk.gov.justice.digital.hmpps.keyworker.integration.events.MergeInformation

@Transactional
@Service
class MergePrisonNumbers(
  private val referenceDataRepository: ReferenceDataRepository,
  private val allocationRepository: AllocationRepository,
) {
  fun merge(mergeInformation: MergeInformation) {
    val reason =
      requireNotNull(referenceDataRepository.findByKey(ReferenceDataKey(DEALLOCATION_REASON, DeallocationReason.MERGED.name)))
    val allocations =
      allocationRepository.findActiveForAllPolicies(mergeInformation.removedNomsNumber).map {
        it.apply {
          if (isActive && allocationRepository.countActiveForPolicy(mergeInformation.nomsNumber, it.policy) > 0) {
            deallocate(reason)
          }
          personIdentifier = mergeInformation.nomsNumber
        }
      }
    allocationRepository.saveAll(allocations)
  }
}
