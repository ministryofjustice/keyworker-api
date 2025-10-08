package uk.gov.justice.digital.hmpps.keyworker.services

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationContext
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationContextHolder
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationPolicy
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceDataDomain.STAFF_STATUS
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceDataRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.StaffConfigRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.getReferenceData
import uk.gov.justice.digital.hmpps.keyworker.domain.of
import uk.gov.justice.digital.hmpps.keyworker.dto.staff.StaffStatus
import java.time.LocalDate

@Transactional
@Service
class ReactivateStaff(
  private val ach: AllocationContextHolder,
  private val staffConfigRepository: StaffConfigRepository,
  private val referenceDataRepository: ReferenceDataRepository,
) {
  fun returningFromLeave(date: LocalDate) {
    AllocationPolicy.entries.forEach { allocationPolicy ->
      ach.setContext(AllocationContext.get().copy(policy = allocationPolicy))
      staffConfigRepository
        .findAllStaffReturningFromLeave(date)
        .takeIf { it.isNotEmpty() }
        ?.run {
          val activeStatus = referenceDataRepository.getReferenceData(STAFF_STATUS of StaffStatus.ACTIVE.name)
          map {
            it.apply {
              status = activeStatus
              reactivateOn = null
            }
          }
        }
    }
  }
}
