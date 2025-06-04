package uk.gov.justice.digital.hmpps.keyworker.services

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationContext
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationPolicy
import uk.gov.justice.digital.hmpps.keyworker.domain.PrisonConfiguration
import uk.gov.justice.digital.hmpps.keyworker.domain.PrisonConfigurationRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceData
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceDataDomain
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceDataKey
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceDataRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.StaffConfigRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.StaffRole
import uk.gov.justice.digital.hmpps.keyworker.domain.StaffRoleRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.StaffWithAllocationCount
import uk.gov.justice.digital.hmpps.keyworker.domain.asCodedDescription
import uk.gov.justice.digital.hmpps.keyworker.domain.of
import uk.gov.justice.digital.hmpps.keyworker.dto.CodedDescription
import uk.gov.justice.digital.hmpps.keyworker.dto.NomisStaffRole
import uk.gov.justice.digital.hmpps.keyworker.dto.StaffRoleInfo
import uk.gov.justice.digital.hmpps.keyworker.dto.StaffSearchRequest
import uk.gov.justice.digital.hmpps.keyworker.dto.StaffSearchRequest.Status.ALL
import uk.gov.justice.digital.hmpps.keyworker.dto.StaffSearchResponse
import uk.gov.justice.digital.hmpps.keyworker.dto.StaffSearchResult
import uk.gov.justice.digital.hmpps.keyworker.integration.PrisonApiClient
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNotesApiClient
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.NoteUsageResponse
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.UsageByAuthorIdRequest.Companion.lastMonthEntries
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.UsageByAuthorIdRequest.Companion.lastMonthSessions
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.UsageByAuthorIdResponse
import uk.gov.justice.digital.hmpps.keyworker.integration.nomisuserroles.NomisStaff
import uk.gov.justice.digital.hmpps.keyworker.integration.nomisuserroles.NomisUserRolesApiClient

@Service
class StaffSearch(
  private val nomisUsersApi: NomisUserRolesApiClient,
  private val prisonApi: PrisonApiClient,
  private val caseNoteApi: CaseNotesApiClient,
  private val staffRoleRepository: StaffRoleRepository,
  private val prisonConfigRepository: PrisonConfigurationRepository,
  private val staffConfigRepository: StaffConfigRepository,
  private val referenceDataRepository: ReferenceDataRepository,
) {
  fun findStaff(
    prisonCode: String,
    request: StaffSearchRequest,
  ): StaffSearchResponse {
    val context = AllocationContext.get()
    val staffMembers = nomisUsersApi.getUsers(prisonCode, request.query).content
    val staffIds = staffMembers.map { it.staffId }.toSet()
    val staffIdStrings = staffIds.map { it.toString() }.toSet()

    val sessions =
      when (context.policy) {
        AllocationPolicy.KEY_WORKER -> caseNoteApi.getUsageByStaffIds(lastMonthSessions(staffIdStrings))
        else -> NoteUsageResponse(emptyMap())
      }
    val entries = caseNoteApi.getUsageByStaffIds(lastMonthEntries(staffIdStrings))

    val roleInfo =
      when (context.policy) {
        AllocationPolicy.KEY_WORKER -> getKeyworkerRoleInfo(prisonCode)
        else ->
          staffRoleRepository
            .findAllByPrisonCodeAndStaffIdIn(prisonCode, staffIds)
            .associate { it.staffId to it.roleInfo() }
      }

    val prisonConfig = prisonConfigRepository.findByCode(prisonCode) ?: PrisonConfiguration.default(prisonCode)
    val staffDetail =
      if (staffIds.isEmpty()) {
        emptyMap()
      } else {
        staffConfigRepository.findAllWithAllocationCount(prisonCode, staffIds).associateBy { it.staffId }
      }

    return StaffSearchResponse(
      staffMembers
        .map {
          it.searchResponse(
            { staffId -> staffDetail[staffId] },
            prisonConfig,
            { staffId -> sessions.content[staffId.toString()]?.firstOrNull() },
            { staffId -> entries.content[staffId.toString()]?.firstOrNull() },
            { staffId -> roleInfo[staffId] },
            lazy { referenceDataRepository.findByKey(ReferenceDataDomain.STAFF_STATUS of StaffSearchRequest.Status.ACTIVE.name)!! },
          )
        }.filter { ss ->
          (request.status == ALL || request.status.name == ss.status.code) &&
            request.hasPolicyStaffRole?.let {
              it && ss.staffRole != null || !it && ss.staffRole == null
            } ?: true
        },
    )
  }

  private fun getKeyworkerRoleInfo(prisonCode: String): Map<Long, StaffRoleInfo> {
    val keyworkers = prisonApi.getKeyworkersForPrison(prisonCode)
    val rd = referenceDataRepository.findAllByKeyIn(keyworkers.flatMap { it.rdKeys() }.toSet()).associateBy { it.key }
    val cd: (ReferenceDataKey) -> CodedDescription = { rdKey -> requireNotNull(rd[rdKey]).asCodedDescription() }
    return keyworkers.associate {
      it.staffId to
        StaffRoleInfo(
          cd(ReferenceDataDomain.STAFF_POSITION of it.position),
          cd(ReferenceDataDomain.STAFF_SCHEDULE_TYPE of it.scheduleType),
          it.hoursPerWeek,
          it.fromDate,
          it.toDate,
        )
    }
  }

  private fun NomisStaff.searchResponse(
    staffConfig: (Long) -> StaffWithAllocationCount?,
    prisonConfig: PrisonConfiguration,
    sessions: (Long) -> UsageByAuthorIdResponse?,
    entries: (Long) -> UsageByAuthorIdResponse?,
    roleInfo: (Long) -> StaffRoleInfo?,
    activeStatusProvider: Lazy<ReferenceData>,
  ): StaffSearchResult {
    val kwa = staffConfig(staffId)
    val status = kwa?.staffConfig?.status ?: activeStatusProvider.value
    return StaffSearchResult(
      staffId,
      firstName,
      lastName,
      status.asCodedDescription(),
      kwa?.staffConfig?.capacity ?: prisonConfig.capacity,
      kwa?.allocationCount ?: 0,
      kwa?.staffConfig?.allowAutoAllocation ?: prisonConfig.allowAutoAllocation,
      sessions(staffId)?.count ?: 0,
      entries(staffId)?.count ?: 0,
      roleInfo(staffId),
      username,
      email,
    )
  }
}

private fun NomisStaffRole.rdKeys() =
  setOf(ReferenceDataDomain.STAFF_POSITION of position, ReferenceDataDomain.STAFF_SCHEDULE_TYPE of scheduleType)

private fun StaffRole.roleInfo() =
  StaffRoleInfo(position.asCodedDescription(), scheduleType.asCodedDescription(), hoursPerWeek, fromDate, toDate)
