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
import uk.gov.justice.digital.hmpps.keyworker.domain.asCodedDescription
import uk.gov.justice.digital.hmpps.keyworker.domain.getReferenceData
import uk.gov.justice.digital.hmpps.keyworker.domain.of
import uk.gov.justice.digital.hmpps.keyworker.domain.toModel
import uk.gov.justice.digital.hmpps.keyworker.dto.NomisStaffRole
import uk.gov.justice.digital.hmpps.keyworker.dto.StaffDetailsRequest
import uk.gov.justice.digital.hmpps.keyworker.dto.StaffRoleInfo
import uk.gov.justice.digital.hmpps.keyworker.dto.StaffStatus
import uk.gov.justice.digital.hmpps.keyworker.dto.map
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

    if (request.setsConfig()) {
      staffConfigRepository.findByStaffId(staffId)?.patch(request) ?: request.createConfig(prisonConfig, staffId)
    }

    val staffRole: StaffRoleInfo? = getStaffRoleIfExists(prisonCode, staffId, request)
    val roleAction =
      request.staffRole.map {
        when {
          request.staffRole.get() == null -> Action.REMOVE
          staffRole == null -> Action.CREATE
          else -> Action.UPDATE
        }
      }
    if (roleAction == Action.CREATE && !request.staffRole.get()!!.isValidToCreate()) {
      throw ResponseStatusException(HttpStatusCode.valueOf(400), "Insufficient parameters to create staff job role")
    }

    if (request.deactivateActiveAllocations.orElse(false) || roleAction == Action.REMOVE) {
      deallocateAllocationsFor(prisonCode, staffId)
    }

    when (roleAction) {
      null -> return
      Action.CREATE -> {
        val jobRoleRequest =
          request.staffRole.get()!!.let {
            StaffJobClassificationRequest(
              position = it.position.get(),
              scheduleType = it.scheduleType.get(),
              hoursPerWeek = it.hoursPerWeek.get(),
              fromDate = it.fromDate.orElse(LocalDate.now()),
              toDate = null,
            )
          }
        policy.nomisUseRoleCode?.let {
          nurApi.setStaffRole(prisonCode, staffId, it, jobRoleRequest)
        } ?: setStaffRole(prisonCode, staffId, jobRoleRequest)
      }

      Action.UPDATE -> {
        policy.nomisUseRoleCode?.let { roleCode ->
          staffRole!!.apply {
            nurApi.setStaffRole(
              prisonCode,
              staffId,
              roleCode,
              request.staffRole.get()!!.let {
                StaffJobClassificationRequest(
                  position = it.position.orElse(position.code),
                  scheduleType = it.scheduleType.orElse(scheduleType.code),
                  hoursPerWeek = it.hoursPerWeek.orElse(hoursPerWeek),
                  fromDate = it.fromDate.orElse(fromDate),
                  toDate = null,
                )
              },
            )
          }
        } ?: run {
          val srr = request.staffRole.get()!!
          staffRoleRepository.findRoleIncludingInactive(prisonCode, staffId)?.apply {
            srr.position.ifPresent {
              this.position = referenceDataRepository.getReferenceData(ReferenceDataDomain.STAFF_POSITION of it)
            }
            srr.scheduleType.ifPresent {
              this.scheduleType =
                referenceDataRepository.getReferenceData(ReferenceDataDomain.STAFF_SCHEDULE_TYPE of it)
            }
            srr.hoursPerWeek.ifPresent { this.hoursPerWeek = it }
            srr.fromDate.ifPresent { this.fromDate = it }
            this.toDate = null
          }
        }
      }

      Action.REMOVE -> {
        staffConfigRepository.deleteByStaffId(staffId)
        policy.nomisUseRoleCode?.also { code ->
          staffRole?.also {
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
      rd[ReferenceDataDomain.STAFF_POSITION]!!,
      rd[ReferenceDataDomain.STAFF_SCHEDULE_TYPE]!!,
      hoursPerWeek,
      fromDate,
      toDate,
    )
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
      request.status.ifPresent {
        status = referenceDataRepository.getReferenceData(ReferenceDataKey(ReferenceDataDomain.STAFF_STATUS, it))
      }
      request.capacity.ifPresent { capacity = it }
      request.allowAutoAllocation.ifPresent { allowAutoAllocation = it }
      request.reactivateOn.ifPresent { reactivateOn = it }
    }

  private fun getStaffRoleIfExists(
    prisonCode: String,
    staffId: Long,
    request: StaffDetailsRequest,
  ): StaffRoleInfo? {
    val policy = AllocationContext.get().policy
    return request.staffRole.map {
      when (policy.nomisUseRoleCode) {
        null -> staffRoleRepository.findRoleIncludingInactive(prisonCode, staffId)?.toModel()
        else -> prisonApi.getKeyworkerForPrison(prisonCode, staffId)?.staffRoleInfo()
      }
    }
  }

  private fun StaffDetailsRequest.createConfig(
    prisonConfig: PrisonConfiguration,
    staffId: Long,
  ): StaffConfiguration =
    staffConfigRepository.save(
      StaffConfiguration(
        referenceDataRepository.getReferenceData(
          ReferenceDataDomain.STAFF_STATUS of if (status.isPresent) status.get() else StaffStatus.ACTIVE.name,
        ),
        capacity.orElse(prisonConfig.maximumCapacity),
        allowAutoAllocation.orElse(true),
        reactivateOn.orElse(null),
        staffId,
      ),
    )
}

enum class Action {
  CREATE,
  UPDATE,
  REMOVE,
}
