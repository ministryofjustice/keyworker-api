package uk.gov.justice.digital.hmpps.keyworker.services

import org.springframework.http.HttpStatusCode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationContext
import uk.gov.justice.digital.hmpps.keyworker.domain.PrisonConfiguration
import uk.gov.justice.digital.hmpps.keyworker.domain.PrisonConfigurationRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceDataDomain
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceDataDomain.DEALLOCATION_REASON
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceDataKey
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceDataRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.StaffAllocationRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.StaffConfigRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.StaffConfiguration
import uk.gov.justice.digital.hmpps.keyworker.domain.StaffRole
import uk.gov.justice.digital.hmpps.keyworker.domain.StaffRoleRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.getReferenceData
import uk.gov.justice.digital.hmpps.keyworker.domain.of
import uk.gov.justice.digital.hmpps.keyworker.dto.NomisStaffRole
import uk.gov.justice.digital.hmpps.keyworker.dto.StaffDetailsRequest
import uk.gov.justice.digital.hmpps.keyworker.integration.PrisonApiClient
import uk.gov.justice.digital.hmpps.keyworker.integration.nomisuserroles.JobClassification
import uk.gov.justice.digital.hmpps.keyworker.integration.nomisuserroles.NomisUserRolesApiClient
import uk.gov.justice.digital.hmpps.keyworker.integration.nomisuserroles.StaffJobClassificationRequest
import uk.gov.justice.digital.hmpps.keyworker.model.DeallocationReason.STAFF_STATUS_CHANGE
import java.time.LocalDate

@Transactional
@Service
class StaffConfigManager(
  private val referenceDataRepository: ReferenceDataRepository,
  private val staffConfigRepository: StaffConfigRepository,
  private val allocationRepository: StaffAllocationRepository,
  private val nurApi: NomisUserRolesApiClient,
  private val staffRoleRepository: StaffRoleRepository,
  private val prisonApi: PrisonApiClient,
  private val prisonConfigRepository: PrisonConfigurationRepository,
) {
  fun setStaffDetails(
    prisonCode: String,
    staffId: Long,
    request: StaffDetailsRequest,
  ) {
    val policy = AllocationContext.get().policy
    val prisonConfig = prisonConfigRepository.findByCode(prisonCode) ?: PrisonConfiguration.default(prisonCode)
    val staffConfig = staffConfigRepository.findByStaffId(staffId)
    val staffRole: Pair<StaffRole?, NomisStaffRole?>? =
      if (request.staffRole.isPresent) {
        if (policy.nomisUseRoleCode != null) {
          Pair(null, prisonApi.getKeyworkerForPrison(prisonCode, staffId))
        } else {
          Pair(staffRoleRepository.findByPrisonCodeAndStaffId(prisonCode, staffId), null)
        }
      } else {
        null
      }

    val createConfig = staffConfig == null
    val createRole =
      request.staffRole.isPresent &&
        request.staffRole.get() != null &&
        staffRole!!.first == null &&
        staffRole.second == null
    val patchRole = request.staffRole.isPresent && request.staffRole.get() != null && !createRole
    val deactivateJobRoleAndConfig = request.staffRole.isPresent && request.staffRole.get() == null
    val removeActiveAllocations = request.deactivateActiveAllocations.orElse(false) || deactivateJobRoleAndConfig

    if (createRole && !request.staffRole.get()!!.isValidToCreate()) {
      throw ResponseStatusException(HttpStatusCode.valueOf(400), "Insufficient parameters to create staff job role")
    }

    if (createConfig) {
      request.apply {
        staffConfigRepository.save(
          StaffConfiguration(
            referenceDataRepository.getReferenceData(
              ReferenceDataKey(ReferenceDataDomain.STAFF_STATUS, if (status.isPresent) status.get() else "ACTIVE"),
            ),
            capacity.orElse(prisonConfig.capacity),
            allowAutoAllocation.orElse(true),
            reactivateOn.orElse(null),
            staffId,
          ),
        )
      }
    } else if (!deactivateJobRoleAndConfig) {
      staffConfig.patch(request)
    }

    if (removeActiveAllocations) {
      deallocateAllocationsFor(prisonCode, staffId)
    }

    if (createRole) {
      val jobRoleRequest =
        request.staffRole.get()!!.let {
          StaffJobClassificationRequest(
            position = it.position.get(),
            scheduleType = it.scheduleType.get(),
            hoursPerWeek = it.hoursPerWeek.get(),
            fromDate =
              it.fromDate.orElse(
                LocalDate.now(),
              ),
            toDate = null,
          )
        }
      policy.nomisUseRoleCode?.let {
        nurApi.setStaffRole(prisonCode, staffId, it, jobRoleRequest)
      } ?: setStaffRole(prisonCode, staffId, jobRoleRequest)
    } else if (patchRole) {
      policy.nomisUseRoleCode?.let { roleCode ->
        staffRole!!.second!!.apply {
          nurApi.setStaffRole(
            prisonCode,
            staffId,
            roleCode,
            request.staffRole.get()!!.let {
              StaffJobClassificationRequest(
                position = it.position.orElse(position),
                scheduleType = it.scheduleType.orElse(scheduleType),
                hoursPerWeek = it.hoursPerWeek.orElse(hoursPerWeek),
                fromDate = it.fromDate.orElse(fromDate),
                toDate = null,
              )
            },
          )
        }
      } ?: run {
        staffRole!!.first!!.apply {
          request.staffRole.get()!!.let { jobRoleRequest ->
            jobRoleRequest.position.ifPresent {
              position =
                referenceDataRepository.getReferenceData(ReferenceDataDomain.STAFF_POSITION of it)
            }
            jobRoleRequest.scheduleType.ifPresent {
              scheduleType =
                referenceDataRepository.getReferenceData(ReferenceDataDomain.STAFF_SCHEDULE_TYPE of it)
            }
            jobRoleRequest.hoursPerWeek.ifPresent { hoursPerWeek = it }
            jobRoleRequest.fromDate.ifPresent { fromDate = it }
          }
        }
      }
    } else if (deactivateJobRoleAndConfig) {
      staffConfigRepository.deleteByStaffId(staffId)

      policy.nomisUseRoleCode?.let {
        staffRole?.second?.apply {
          nurApi.setStaffRole(
            prisonCode,
            staffId,
            it,
            StaffJobClassificationRequest(
              position = position,
              scheduleType = scheduleType,
              hoursPerWeek = hoursPerWeek,
              fromDate = fromDate,
              toDate = LocalDate.now(),
            ),
          )
        }
      } ?: run {
        staffRole?.first?.apply {
          toDate = LocalDate.now()
        }
      }
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

  private fun StaffConfiguration.patch(request: StaffDetailsRequest) =
    apply {
      request.status.ifPresent { status = referenceDataRepository.getReferenceData(ReferenceDataKey(ReferenceDataDomain.STAFF_STATUS, it)) }
      request.capacity.ifPresent { capacity = it }
      request.allowAutoAllocation.ifPresent { allowAutoAllocation = it }
      request.reactivateOn.ifPresent { reactivateOn = it }
    }
}
