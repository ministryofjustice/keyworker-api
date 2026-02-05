package uk.gov.justice.digital.hmpps.keyworker.services.staff

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationContext
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationPolicy
import uk.gov.justice.digital.hmpps.keyworker.domain.AllocationRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.PrisonConfiguration
import uk.gov.justice.digital.hmpps.keyworker.domain.PrisonConfigurationRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceDataDomain
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceDataDomain.DEALLOCATION_REASON
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceDataKey
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceDataRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.StaffConfigRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.StaffConfiguration
import uk.gov.justice.digital.hmpps.keyworker.domain.StaffRole
import uk.gov.justice.digital.hmpps.keyworker.domain.StaffRoleRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.asCodedDescription
import uk.gov.justice.digital.hmpps.keyworker.domain.getReferenceData
import uk.gov.justice.digital.hmpps.keyworker.domain.of
import uk.gov.justice.digital.hmpps.keyworker.domain.toModel
import uk.gov.justice.digital.hmpps.keyworker.integration.nomisuserroles.JobClassification
import uk.gov.justice.digital.hmpps.keyworker.integration.nomisuserroles.NomisUserRolesApiClient
import uk.gov.justice.digital.hmpps.keyworker.integration.nomisuserroles.StaffJobClassificationRequest
import uk.gov.justice.digital.hmpps.keyworker.integration.prisonapi.NomisStaffRole
import uk.gov.justice.digital.hmpps.keyworker.integration.prisonapi.PrisonApiClient
import uk.gov.justice.digital.hmpps.keyworker.model.DeallocationReason
import uk.gov.justice.digital.hmpps.keyworker.model.staff.StaffDetailsRequest
import uk.gov.justice.digital.hmpps.keyworker.model.staff.StaffRoleInfo
import uk.gov.justice.digital.hmpps.keyworker.model.staff.StaffRoleRequest
import uk.gov.justice.digital.hmpps.keyworker.model.staff.StaffStatus
import java.time.LocalDate

@Transactional
@Service
class StaffConfigManager(
  private val referenceDataRepository: ReferenceDataRepository,
  private val staffConfigRepository: StaffConfigRepository,
  private val allocationRepository: AllocationRepository,
  private val nurApi: NomisUserRolesApiClient,
  private val staffRoleRepository: StaffRoleRepository,
  private val prisonApi: PrisonApiClient,
  private val prisonConfigRepository: PrisonConfigurationRepository,
) {
  fun mergeStaffDetails(
    prisonCode: String,
    staffId: Long,
    request: StaffDetailsRequest,
  ) {
    val prisonConfig = prisonConfigRepository.findByCode(prisonCode) ?: PrisonConfiguration.default(prisonCode)
    staffConfigRepository.findByStaffId(staffId)?.update(request) ?: request.createConfig(staffId, prisonConfig)
    request.staffRole.setFor(prisonCode, staffId)

    if (request.deactivateActiveAllocations) {
      deallocateAllocationsFor(prisonCode, staffId)
    }
  }

  fun removeStaffRole(
    prisonCode: String,
    staffId: Long,
  ) {
    val policy = AllocationContext.get().requiredPolicy()
    deallocateAllocationsFor(prisonCode, staffId)
    staffConfigRepository.deleteByStaffId(staffId)
    policy.nomisUserRoleCode?.also { code ->
      findStaffRole(prisonCode, staffId)?.also {
        nurApi.setStaffRole(
          prisonCode,
          staffId,
          code,
          StaffJobClassificationRequest(
            position = it.position.code,
            scheduleType = it.scheduleType.code,
            hoursPerWeek = it.hoursPerWeek,
            fromDate = it.fromDate,
            toDate = LocalDate.now(),
          ),
        )
      }
    } ?: run {
      staffRoleRepository.findByPrisonCodeAndStaffId(prisonCode, staffId)?.apply {
        toDate = LocalDate.now()
      }
    }
  }

  private fun deallocateAllocationsFor(
    prisonCode: String,
    staffId: Long,
  ) {
    val deallocationReason =
      requireNotNull(referenceDataRepository.findByKey(DEALLOCATION_REASON of DeallocationReason.STAFF_STATUS_CHANGE.name))
    allocationRepository.findActiveForPrisonStaff(prisonCode, staffId).forEach { allocation ->
      allocation.deallocate(deallocationReason)
    }
  }

  private fun StaffDetailsRequest.createConfig(
    staffId: Long,
    defaults: PrisonConfiguration,
  ) {
    if (this changes defaults) {
      staffConfigRepository.save(
        StaffConfiguration(
          referenceDataRepository.getReferenceData(ReferenceDataDomain.STAFF_STATUS of status),
          capacity,
          reactivateOn,
          staffId,
        ),
      )
    }
  }

  private infix fun StaffDetailsRequest.changes(pc: PrisonConfiguration): Boolean =
    status != StaffStatus.ACTIVE.name || capacity != pc.capacity || reactivateOn != null

  private fun StaffConfiguration.update(request: StaffDetailsRequest) =
    apply {
      status =
        referenceDataRepository.getReferenceData(ReferenceDataKey(ReferenceDataDomain.STAFF_STATUS, request.status))
      capacity = request.capacity
      reactivateOn = request.reactivateOn
    }

  private fun findStaffRole(
    prisonCode: String,
    staffId: Long,
  ): StaffRoleInfo? {
    val policy = AllocationContext.get().requiredPolicy()
    return when (policy.nomisUserRoleCode) {
      null -> staffRoleRepository.findRoleIncludingInactiveForPolicy(prisonCode, staffId, policy.name)?.toModel()
      else -> prisonApi.getKeyworkerForPrison(prisonCode, staffId)?.staffRoleInfo()
    }
  }

  private fun NomisStaffRole.staffRoleInfo(): StaffRoleInfo {
    val rd =
      referenceDataRepository
        .findAllByKeyIn(
          setOf(
            ReferenceDataDomain.STAFF_SCHEDULE_TYPE of scheduleType,
            ReferenceDataDomain.STAFF_POSITION of position,
          ),
        ).associate { it.key.domain to it.asCodedDescription() }

    return StaffRoleInfo(
      requireNotNull(rd[ReferenceDataDomain.STAFF_POSITION]),
      requireNotNull(rd[ReferenceDataDomain.STAFF_SCHEDULE_TYPE]),
      hoursPerWeek,
      fromDate,
      toDate,
    )
  }

  private fun StaffRoleRequest.setFor(
    prisonCode: String,
    staffId: Long,
  ) {
    val policy = AllocationContext.get().requiredPolicy()
    val jobRoleRequest =
      StaffJobClassificationRequest(
        position = position,
        scheduleType = scheduleType,
        hoursPerWeek = hoursPerWeek,
        fromDate = fromDate,
        toDate = toDate,
      )
    policy.nomisUserRoleCode?.let {
      nurApi.setStaffRole(prisonCode, staffId, it, jobRoleRequest)
    } ?: setStaffRole(policy, prisonCode, staffId, jobRoleRequest)
  }

  private fun setStaffRole(
    policy: AllocationPolicy,
    prisonCode: String,
    staffId: Long,
    request: JobClassification,
  ): StaffRole =
    staffRoleRepository.findRoleIncludingInactiveForPolicy(prisonCode, staffId, policy.name)?.apply {
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
}
