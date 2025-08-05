package uk.gov.justice.digital.hmpps.keyworker.services

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceDataDomain.STAFF_STATUS
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceDataRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.StaffConfigRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.getReferenceData
import uk.gov.justice.digital.hmpps.keyworker.domain.of
import uk.gov.justice.digital.hmpps.keyworker.model.StaffStatus.ACTIVE
import java.time.LocalDate

@Transactional
@Service
class ReactivateStaff(
  private val staffConfigRepository: StaffConfigRepository,
  private val referenceDataRepository: ReferenceDataRepository,
) {
  fun returningFromLeave(date: LocalDate) {
    staffConfigRepository
      .findAllStaffReturningFromLeave(date.plusDays(1))
      .takeIf { it.isNotEmpty() }
      ?.run {
        val activeStatus = referenceDataRepository.getReferenceData(STAFF_STATUS of ACTIVE.name)
        map {
          it.apply {
            status = activeStatus
            reactivateOn = null
          }
        }
      }
  }
}
