package uk.gov.justice.digital.hmpps.keyworker.services.staff

import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationPolicy
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceDataDomain
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceDataKey
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceDataRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.StaffRole
import uk.gov.justice.digital.hmpps.keyworker.domain.StaffRoleRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.asCodedDescription
import uk.gov.justice.digital.hmpps.keyworker.domain.of
import uk.gov.justice.digital.hmpps.keyworker.integration.prisonapi.NomisStaffRole
import uk.gov.justice.digital.hmpps.keyworker.integration.prisonapi.PrisonApiClient
import uk.gov.justice.digital.hmpps.keyworker.model.CodedDescription
import uk.gov.justice.digital.hmpps.keyworker.model.staff.StaffRoleInfo
import uk.gov.justice.digital.hmpps.keyworker.model.staff.StaffSummary

interface StaffRoleRetriever {
  val policies: Set<AllocationPolicy>

  fun getStaffRoles(prisonCode: String): Map<Long, StaffRoleInfo>

  fun getStaffWithRoles(prisonCode: String): List<StaffSummaryWithRole>
}

data class StaffSummaryWithRole(
  val staff: StaffSummary,
  val role: StaffRoleInfo,
)

@Component
class NomisRoleRetriever(
  private val prisonApi: PrisonApiClient,
  private val referenceDataRepository: ReferenceDataRepository,
) : StaffRoleRetriever {
  override val policies = setOf(AllocationPolicy.KEY_WORKER)

  override fun getStaffRoles(prisonCode: String): Map<Long, StaffRoleInfo> {
    val keyworkers = prisonApi.getKeyworkersForPrison(prisonCode)
    return keyworkers.associate {
      it.staffId to it.staffRole(keyworkers.referenceData())
    }
  }

  override fun getStaffWithRoles(prisonCode: String): List<StaffSummaryWithRole> {
    val keyworkers = prisonApi.getKeyworkersForPrison(prisonCode)
    return keyworkers.map {
      StaffSummaryWithRole(
        StaffSummary(it.staffId, it.firstName, it.lastName),
        it.staffRole(keyworkers.referenceData()),
      )
    }
  }

  private fun NomisStaffRole.rdKeys() =
    setOf(ReferenceDataDomain.STAFF_POSITION of position, ReferenceDataDomain.STAFF_SCHEDULE_TYPE of scheduleType)

  private fun List<NomisStaffRole>.referenceData(): (ReferenceDataKey) -> CodedDescription {
    val rd = referenceDataRepository.findAllByKeyIn(flatMap { it.rdKeys() }.toSet()).associateBy { it.key }
    return { rdKey -> requireNotNull(rd[rdKey]).asCodedDescription() }
  }

  private fun NomisStaffRole.staffRole(rd: (ReferenceDataKey) -> CodedDescription) =
    StaffRoleInfo(
      rd(ReferenceDataDomain.STAFF_POSITION of position),
      rd(ReferenceDataDomain.STAFF_SCHEDULE_TYPE of scheduleType),
      hoursPerWeek,
      fromDate,
      toDate,
    )
}

@Component
class LocalRoleRetriever(
  private val prisonApi: PrisonApiClient,
  private val staffRoleRepository: StaffRoleRepository,
) : StaffRoleRetriever {
  override val policies = setOf(AllocationPolicy.PERSONAL_OFFICER)

  override fun getStaffRoles(prisonCode: String): Map<Long, StaffRoleInfo> =
    staffRoleRepository.findAllByPrisonCode(prisonCode).associate { it.staffId to it.roleInfo() }

  override fun getStaffWithRoles(prisonCode: String): List<StaffSummaryWithRole> {
    val roles = staffRoleRepository.findAllByPrisonCode(prisonCode).associate { it.staffId to it.roleInfo() }
    val staff = prisonApi.getStaffSummariesFromIds(roles.keys)
    return staff.map { StaffSummaryWithRole(it, checkNotNull(roles[it.staffId])) }
  }

  private fun StaffRole.roleInfo() =
    StaffRoleInfo(position.asCodedDescription(), scheduleType.asCodedDescription(), hoursPerWeek, fromDate, toDate)
}
