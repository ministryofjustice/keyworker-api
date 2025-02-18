package uk.gov.justice.digital.hmpps.keyworker.services

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.keyworker.integration.events.MergeInformation
import uk.gov.justice.digital.hmpps.keyworker.model.DeallocationReason
import uk.gov.justice.digital.hmpps.keyworker.repository.OffenderKeyworkerRepository
import java.time.LocalDateTime

@Transactional
@Service
class MergePrisonNumbers(
  private val okr: OffenderKeyworkerRepository,
) {
  fun merge(mergeInformation: MergeInformation) {
    okr.findByOffenderNo(mergeInformation.removedNomsNumber).forEach { okw ->
      if (okw.isActive && okr.countByActiveAndOffenderNo(true, mergeInformation.nomsNumber) > 0) {
        okw.deallocate(LocalDateTime.now(), DeallocationReason.MERGED)
      }

      okw.offenderNo = mergeInformation.nomsNumber
    }
  }
}
