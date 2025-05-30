package uk.gov.justice.digital.hmpps.keyworker.services

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationContext
import uk.gov.justice.digital.hmpps.keyworker.domain.KeyworkerAllocationRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.PrisonConfigurationRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceDataDomain
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceDataDomain.DEALLOCATION_REASON
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceDataRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.StaffConfigRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.StaffConfiguration
import uk.gov.justice.digital.hmpps.keyworker.domain.StaffRole
import uk.gov.justice.digital.hmpps.keyworker.domain.StaffRoleRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.getConfigFor
import uk.gov.justice.digital.hmpps.keyworker.domain.getKeyworkerStatus
import uk.gov.justice.digital.hmpps.keyworker.domain.getReferenceData
import uk.gov.justice.digital.hmpps.keyworker.domain.of
import uk.gov.justice.digital.hmpps.keyworker.dto.KeyworkerConfigRequest
import uk.gov.justice.digital.hmpps.keyworker.dto.StaffConfigRequest
import uk.gov.justice.digital.hmpps.keyworker.integration.nomisuserroles.JobClassification
import uk.gov.justice.digital.hmpps.keyworker.integration.nomisuserroles.NomisUserRolesApiClient
import uk.gov.justice.digital.hmpps.keyworker.model.DeallocationReason.STAFF_STATUS_CHANGE

@Transactional
@Service
class StaffConfigManager(
  private val pcr: PrisonConfigurationRepository,
  private val referenceDataRepository: ReferenceDataRepository,
  private val staffConfigRepository: StaffConfigRepository,
  private val allocationRepository: KeyworkerAllocationRepository,
  private val nurApi: NomisUserRolesApiClient,
  private val staffRoleRepository: StaffRoleRepository,
) {
  fun configure(
    prisonCode: String,
    staffId: Long,
    request: StaffConfigRequest,
  ) {
    val prisonConfig = pcr.getConfigFor(prisonCode)
    createStaffJobClassification(prisonCode, staffId, request)
    staffConfigRepository.save(
      staffConfigRepository.findByStaffId(staffId)?.update(request)
        ?: request.asConfig(staffId, prisonConfig.capacity),
    )

    if (request.deactivateActiveAllocations) {
      val deallocationReason =
        requireNotNull(referenceDataRepository.findByKey(DEALLOCATION_REASON of STAFF_STATUS_CHANGE.reasonCode))
      allocationRepository.findActiveForPrisonStaff(prisonCode, staffId).forEach {
        it.deallocate(deallocationReason)
      }
    }
  }

  private fun createStaffJobClassification(
    prisonCode: String,
    staffId: Long,
    request: StaffConfigRequest,
  ) {
    AllocationContext.get().policy.nomisUseRoleCode?.let {
      nurApi.setStaffRole(prisonCode, staffId, it, request.classification)
    } ?: setStaffRole(prisonCode, staffId, request.classification)
  }

  private fun setStaffRole(
    prisonCode: String,
    staffId: Long,
    request: JobClassification,
  ): StaffRole =
    staffRoleRepository.findByPrisonCodeAndStaffId(prisonCode, staffId)?.apply {
      position = referenceDataRepository.getReferenceData(ReferenceDataDomain.STAFF_POS of request.position)
      scheduleType = referenceDataRepository.getReferenceData(ReferenceDataDomain.SCHEDULE_TYPE of request.scheduleType)
      hoursPerWeek = request.hoursPerWeek
      fromDate = request.fromDate
      toDate = request.toDate
    } ?: staffRoleRepository.save(request.asStaffRole(prisonCode, staffId))

  private fun JobClassification.asStaffRole(
    prisonCode: String,
    staffId: Long,
  ) = StaffRole(
    referenceDataRepository.getReferenceData(ReferenceDataDomain.STAFF_POS of position),
    referenceDataRepository.getReferenceData(ReferenceDataDomain.SCHEDULE_TYPE of scheduleType),
    hoursPerWeek,
    fromDate,
    toDate,
    prisonCode,
    staffId,
  )

  fun configureKeyworker(
    prisonCode: String,
    staffId: Long,
    request: KeyworkerConfigRequest,
  ) {
    staffConfigRepository.save(
      staffConfigRepository.findByStaffId(staffId)?.update(request) ?: request.asConfig(staffId),
    )

    if (request.deactivateActiveAllocations) {
      val deallocationReason =
        requireNotNull(referenceDataRepository.findByKey(DEALLOCATION_REASON of STAFF_STATUS_CHANGE.reasonCode))
      allocationRepository.findActiveForPrisonStaff(prisonCode, staffId).forEach {
        it.deallocate(deallocationReason)
      }
    }
  }

  private fun StaffConfigRequest.asConfig(
    staffId: Long,
    prisonCapacity: Int,
  ) = StaffConfiguration(
    referenceDataRepository.getKeyworkerStatus(status),
    capacity ?: prisonCapacity,
    !removeFromAutoAllocation,
    reactivateOn,
    staffId,
  )

  private fun KeyworkerConfigRequest.asConfig(staffId: Long) =
    StaffConfiguration(
      referenceDataRepository.getKeyworkerStatus(status),
      capacity,
      !removeFromAutoAllocation,
      reactivateOn,
      staffId,
    )

  private fun StaffConfiguration.update(request: KeyworkerConfigRequest) =
    apply {
      status = referenceDataRepository.getKeyworkerStatus(request.status)
      capacity = request.capacity
      allowAutoAllocation = !request.removeFromAutoAllocation
      reactivateOn = request.reactivateOn
    }

  private fun StaffConfiguration.update(request: StaffConfigRequest) =
    apply {
      status = referenceDataRepository.getKeyworkerStatus(request.status)
      request.capacity?.also { capacity = it }
      allowAutoAllocation = !request.removeFromAutoAllocation
      reactivateOn = request.reactivateOn
    }
}
