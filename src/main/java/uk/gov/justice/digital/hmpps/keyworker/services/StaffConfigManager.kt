package uk.gov.justice.digital.hmpps.keyworker.services

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationContext
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceDataDomain
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceDataDomain.DEALLOCATION_REASON
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceDataRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.StaffAllocationRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.StaffConfigRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.StaffConfiguration
import uk.gov.justice.digital.hmpps.keyworker.domain.StaffRole
import uk.gov.justice.digital.hmpps.keyworker.domain.StaffRoleRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.getKeyworkerStatus
import uk.gov.justice.digital.hmpps.keyworker.domain.getReferenceData
import uk.gov.justice.digital.hmpps.keyworker.domain.of
import uk.gov.justice.digital.hmpps.keyworker.dto.StaffConfigRequest
import uk.gov.justice.digital.hmpps.keyworker.integration.nomisuserroles.JobClassification
import uk.gov.justice.digital.hmpps.keyworker.integration.nomisuserroles.NomisUserRolesApiClient
import uk.gov.justice.digital.hmpps.keyworker.integration.nomisuserroles.StaffJobClassificationRequest
import uk.gov.justice.digital.hmpps.keyworker.model.DeallocationReason.STAFF_STATUS_CHANGE

@Transactional
@Service
class StaffConfigManager(
  private val referenceDataRepository: ReferenceDataRepository,
  private val staffConfigRepository: StaffConfigRepository,
  private val allocationRepository: StaffAllocationRepository,
  private val nurApi: NomisUserRolesApiClient,
  private val staffRoleRepository: StaffRoleRepository,
) {
  fun setStaffConfiguration(
    prisonCode: String,
    staffId: Long,
    request: StaffConfigRequest,
  ) {
    staffConfigRepository.save(
      staffConfigRepository.findByStaffId(staffId)?.update(request)
        ?: request.asConfig(staffId),
    )

    if (request.deactivateActiveAllocations) {
      deallocateAllocationsFor(prisonCode, staffId)
    }
  }

  fun setStaffJobClassification(
    prisonCode: String,
    staffId: Long,
    request: StaffJobClassificationRequest,
  ) {
    AllocationContext.get().policy.nomisUseRoleCode?.let {
      nurApi.setStaffRole(prisonCode, staffId, it, request)
    } ?: setStaffRole(prisonCode, staffId, request)
    if (request.toDate != null) {
      staffConfigRepository.deleteByStaffId(staffId)
      deallocateAllocationsFor(prisonCode, staffId)
    }
  }

  private fun deallocateAllocationsFor(
    prisonCode: String,
    staffId: Long,
  ) {
    val deallocationReason =
      requireNotNull(referenceDataRepository.findByKey(DEALLOCATION_REASON of STAFF_STATUS_CHANGE.reasonCode))
    allocationRepository.findActiveForPrisonStaff(prisonCode, staffId).forEach { allocation ->
      allocation.deallocate(deallocationReason)
    }
  }

  private fun setStaffRole(
    prisonCode: String,
    staffId: Long,
    request: JobClassification,
  ): StaffRole =
    staffRoleRepository.findByPrisonCodeAndStaffId(prisonCode, staffId)?.apply {
      position = referenceDataRepository.getReferenceData(ReferenceDataDomain.STAFF_POSITION of request.position)
      scheduleType =
        referenceDataRepository.getReferenceData(ReferenceDataDomain.STAFF_SCHEDULE_TYPE of request.scheduleType)
      hoursPerWeek = request.hoursPerWeek
      fromDate = request.fromDate
      toDate = request.toDate
    } ?: staffRoleRepository.save(request.asStaffRole(prisonCode, staffId))

  private fun JobClassification.asStaffRole(
    prisonCode: String,
    staffId: Long,
  ) = StaffRole(
    referenceDataRepository.getReferenceData(ReferenceDataDomain.STAFF_POSITION of position),
    referenceDataRepository.getReferenceData(ReferenceDataDomain.STAFF_SCHEDULE_TYPE of scheduleType),
    hoursPerWeek,
    fromDate,
    toDate,
    prisonCode,
    staffId,
  )

  private fun StaffConfigRequest.asConfig(staffId: Long) =
    StaffConfiguration(
      referenceDataRepository.getKeyworkerStatus(status),
      capacity,
      !removeFromAutoAllocation,
      reactivateOn,
      staffId,
    )

  private fun StaffConfiguration.update(request: StaffConfigRequest) =
    apply {
      status = referenceDataRepository.getKeyworkerStatus(request.status)
      capacity = request.capacity
      allowAutoAllocation = !request.removeFromAutoAllocation
      reactivateOn = request.reactivateOn
    }
}
