package uk.gov.justice.digital.hmpps.keyworker.services

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.keyworker.domain.AllocationRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceDataDomain.DEALLOCATION_REASON
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceDataKey
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceDataRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.findActiveFor
import uk.gov.justice.digital.hmpps.keyworker.integration.events.MergeInformation
import uk.gov.justice.digital.hmpps.keyworker.model.DeallocationReason.MERGED

@Transactional
@Service
class MergePrisonNumbers(
  private val referenceDataRepository: ReferenceDataRepository,
  private val allocationRepository: AllocationRepository,
  private val caseNoteService: AllocationCaseNoteService,
) {
  fun merge(mergeInformation: MergeInformation) {
    val reason =
      requireNotNull(referenceDataRepository.findByKey(ReferenceDataKey(DEALLOCATION_REASON, MERGED.reasonCode)))
    allocationRepository.findActiveFor(mergeInformation.removedNomsNumber).forEach { allocation ->
      if (allocation.isActive && allocationRepository.countAllByPersonIdentifierAndIsActiveTrue(mergeInformation.nomsNumber) > 0) {
        allocation.deallocate(reason)
      }

      allocation.personIdentifier = mergeInformation.nomsNumber
    }
    caseNoteService.merge(mergeInformation)
  }
}
