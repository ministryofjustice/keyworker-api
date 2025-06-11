package uk.gov.justice.digital.hmpps.keyworker.services

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceDataDomain.DEALLOCATION_REASON
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceDataKey
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceDataRepository
import uk.gov.justice.digital.hmpps.keyworker.integration.events.MergeInformation
import uk.gov.justice.digital.hmpps.keyworker.model.DeallocationReason.MERGED
import uk.gov.justice.digital.hmpps.keyworker.repository.LegacyKeyworkerAllocationRepository
import java.time.LocalDateTime

@Transactional
@Service
class MergePrisonNumbers(
  private val okr: LegacyKeyworkerAllocationRepository,
  private val referenceDataRepository: ReferenceDataRepository,
) {
  fun merge(mergeInformation: MergeInformation) {
    val reason = referenceDataRepository.findByKey(ReferenceDataKey(DEALLOCATION_REASON, MERGED.reasonCode))
    okr.findByPersonIdentifier(mergeInformation.removedNomsNumber).forEach { okw ->
      if (okw.isActive && okr.countByActiveAndPersonIdentifier(true, mergeInformation.nomsNumber) > 0) {
        okw.deallocate(LocalDateTime.now(), reason)
      }

      okw.personIdentifier = mergeInformation.nomsNumber
    }
  }
}
