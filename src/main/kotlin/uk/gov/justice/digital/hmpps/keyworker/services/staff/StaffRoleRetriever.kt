package uk.gov.justice.digital.hmpps.keyworker.services.staff

import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationContext
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationPolicy
import uk.gov.justice.digital.hmpps.keyworker.domain.StaffRole
import uk.gov.justice.digital.hmpps.keyworker.domain.StaffRoleRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.asCodedDescription
import uk.gov.justice.digital.hmpps.keyworker.domain.toModel
import uk.gov.justice.digital.hmpps.keyworker.integration.prisonapi.PrisonApiClient
import uk.gov.justice.digital.hmpps.keyworker.model.staff.StaffRoleInfo
import uk.gov.justice.digital.hmpps.keyworker.model.staff.StaffSummary

interface StaffRoleRetriever {
  val policies: Set<AllocationPolicy>

  fun getActivePoliciesForPrison(
    prisonCode: String,
    staffId: Long,
  ): Set<AllocationPolicy>

  fun getStaffRoles(prisonCode: String): Map<Long, StaffRoleInfo>

  fun getStaffWithRoles(prisonCode: String): List<StaffSummaryWithRole>

  fun getStaffWithRole(
    prisonCode: String,
    staffId: Long,
  ): StaffSummaryWithRole?
}

data class StaffSummaryWithRole(
  val staff: StaffSummary,
  val role: StaffRoleInfo?,
)

@Component
class LocalRoleRetriever(
  private val prisonApi: PrisonApiClient,
  private val staffRoleRepository: StaffRoleRepository,
) : StaffRoleRetriever {
  override val policies = setOf(AllocationPolicy.KEY_WORKER, AllocationPolicy.PERSONAL_OFFICER)

  override fun getActivePoliciesForPrison(
    prisonCode: String,
    staffId: Long,
  ): Set<AllocationPolicy> = staffRoleRepository.findActiveStaffPoliciesForPrison(prisonCode, staffId)

  override fun getStaffRoles(prisonCode: String): Map<Long, StaffRoleInfo> =
    staffRoleRepository
      .findAllByPrisonCodeAndPolicy(prisonCode, AllocationContext.get().requiredPolicy().name)
      .associate { it.staffId to it.roleInfo() }

  override fun getStaffWithRoles(prisonCode: String): List<StaffSummaryWithRole> {
    val roles =
      staffRoleRepository
        .findAllByPrisonCodeAndPolicy(prisonCode, AllocationContext.get().requiredPolicy().name)
        .associate { it.staffId to it.roleInfo() }
    val staff = prisonApi.getStaffSummariesFromIds(roles.keys)
    return staff.map { StaffSummaryWithRole(it, checkNotNull(roles[it.staffId])) }
  }

  override fun getStaffWithRole(
    prisonCode: String,
    staffId: Long,
  ): StaffSummaryWithRole? =
    prisonApi.getStaffSummariesFromIds(setOf(staffId)).firstOrNull()?.let {
      StaffSummaryWithRole(
        it,
        staffRoleRepository
          .findRoleIncludingInactiveForPolicy(prisonCode, staffId, AllocationContext.get().requiredPolicy().name)
          ?.toModel(),
      )
    }

  private fun StaffRole.roleInfo() =
    StaffRoleInfo(position.asCodedDescription(), scheduleType.asCodedDescription(), hoursPerWeek, fromDate, toDate)
}
