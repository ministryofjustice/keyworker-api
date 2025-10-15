package uk.gov.justice.digital.hmpps.keyworker.services

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.keyworker.domain.AllocationRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceDataDomain.DEALLOCATION_REASON
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceDataRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.getReferenceData
import uk.gov.justice.digital.hmpps.keyworker.domain.of
import uk.gov.justice.digital.hmpps.keyworker.integration.events.domain.MergeInformation
import uk.gov.justice.digital.hmpps.keyworker.model.DeallocationReason

@Transactional
@Service
class MergePrisonNumbers(
  private val referenceDataRepository: ReferenceDataRepository,
  private val allocationRepository: AllocationRepository,
) {
  fun merge(mergeInformation: MergeInformation) {
    allocationRepository.findByPersonIdentifierAndIsActiveTrue(mergeInformation.removedNomsNumber)?.apply {
      if (isActive && allocationRepository.countActiveForPolicy(mergeInformation.nomsNumber, policy) > 0) {
        deallocate(referenceDataRepository.getReferenceData(DEALLOCATION_REASON of DeallocationReason.MERGED.name))
      }
      personIdentifier = mergeInformation.nomsNumber
    }
  }
}
