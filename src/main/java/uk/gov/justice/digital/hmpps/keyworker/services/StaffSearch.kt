package uk.gov.justice.digital.hmpps.keyworker.services

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationContext
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationPolicy
import uk.gov.justice.digital.hmpps.keyworker.domain.Allocation
import uk.gov.justice.digital.hmpps.keyworker.domain.AllocationRepository
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
import uk.gov.justice.digital.hmpps.keyworker.domain.filterApplicable
import uk.gov.justice.digital.hmpps.keyworker.domain.of
import uk.gov.justice.digital.hmpps.keyworker.dto.AllocatableSearchRequest
import uk.gov.justice.digital.hmpps.keyworker.dto.AllocatableSearchResponse
import uk.gov.justice.digital.hmpps.keyworker.dto.AllocatableStaff
import uk.gov.justice.digital.hmpps.keyworker.dto.AllocatableSummary
import uk.gov.justice.digital.hmpps.keyworker.dto.CodedDescription
import uk.gov.justice.digital.hmpps.keyworker.dto.NomisStaffRole
import uk.gov.justice.digital.hmpps.keyworker.dto.ReportingPeriod
import uk.gov.justice.digital.hmpps.keyworker.dto.StaffRoleInfo
import uk.gov.justice.digital.hmpps.keyworker.dto.StaffSearchRequest
import uk.gov.justice.digital.hmpps.keyworker.dto.StaffSearchResponse
import uk.gov.justice.digital.hmpps.keyworker.dto.StaffSearchResult
import uk.gov.justice.digital.hmpps.keyworker.dto.StaffStatus
import uk.gov.justice.digital.hmpps.keyworker.dto.StaffSummary
import uk.gov.justice.digital.hmpps.keyworker.dto.StaffWithRole
import uk.gov.justice.digital.hmpps.keyworker.integration.PrisonApiClient
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNotesApiClient
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.NoteUsageResponse
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.UsageByAuthorIdRequest.Companion.lastMonthEntries
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.UsageByAuthorIdRequest.Companion.lastMonthSessions
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.UsageByAuthorIdResponse
import uk.gov.justice.digital.hmpps.keyworker.integration.nomisuserroles.NomisUserRolesApiClient
import uk.gov.justice.digital.hmpps.keyworker.services.recordedevents.RecordedEventRetriever
import uk.gov.justice.digital.hmpps.keyworker.services.recordedevents.RecordedEventSummaries

@Service
class StaffSearch(
  private val nomisUsersApi: NomisUserRolesApiClient,
  private val prisonApi: PrisonApiClient,
  private val caseNoteApi: CaseNotesApiClient,
  private val recordedEventRetriever: RecordedEventRetriever,
  private val staffRoleRepository: StaffRoleRepository,
  private val allocationRepository: AllocationRepository,
  private val prisonConfigRepository: PrisonConfigurationRepository,
  private val staffConfigRepository: StaffConfigRepository,
  private val referenceDataRepository: ReferenceDataRepository,
) {
  fun searchForStaff(
    prisonCode: String,
    request: StaffSearchRequest,
  ): StaffSearchResponse {
    val context = AllocationContext.get()
    val staffMembers = searchStaff(prisonCode, request.query)
    val staffIds = staffMembers.map { it.staffId }.toSet()
    val staffIdStrings = staffIds.map { it.toString() }.toSet()

    val sessions =
      when (context.policy) {
        AllocationPolicy.KEY_WORKER -> caseNoteApi.getUsageByStaffIds(lastMonthSessions(staffIdStrings))
        else -> NoteUsageResponse(emptyMap())
      }
    val entries = caseNoteApi.getUsageByStaffIds(lastMonthEntries(staffIdStrings))

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
            lazy { referenceDataRepository.findByKey(ReferenceDataDomain.STAFF_STATUS of StaffStatus.ACTIVE.name)!! },
          )
        }.filter { ss ->
          (request.status == StaffStatus.ALL || request.status.name == ss.status.code) &&
            request.hasPolicyStaffRole?.let {
              it && ss.staffRole != null || !it && ss.staffRole == null
            } ?: true
        },
    )
  }

  private fun searchStaff(
    prisonCode: String,
    nameFilter: String? = null,
  ): List<StaffWithRole> {
    val context = AllocationContext.get()
    val staffMembers = nomisUsersApi.getUsers(prisonCode, nameFilter).content
    val roleInfo =
      when (context.policy) {
        AllocationPolicy.KEY_WORKER -> getKeyworkerRoleInfo(prisonCode)
        else ->
          staffRoleRepository
            .findAllByPrisonCodeAndStaffIdIn(prisonCode, staffMembers.map { it.staffId }.toSet())
            .associate { it.staffId to it.roleInfo() }
      }
    return staffMembers
      .filter { it.staffStatus == "ACTIVE" }
      .map {
        StaffWithRole(
          it.staffId,
          it.firstName,
          it.lastName,
          roleInfo[it.staffId],
          it.username,
          it.email,
        )
      }
  }

  fun searchForAllocatableStaff(
    prisonCode: String,
    request: AllocatableSearchRequest,
    includeStats: Boolean,
  ): AllocatableSearchResponse {
    val reportingPeriod = ReportingPeriod.currentMonth()

    val prisonConfig = prisonConfigRepository.findByCode(prisonCode) ?: PrisonConfiguration.default(prisonCode)

    val staffMembers =
      findAllocatableStaff(prisonCode)
        .filter { it.staffMember.matches(request.query) }
        .associateBy { it.staffMember.staffId }

    val applicableAllocationsByStaff =
      allocationRepository
        .findActiveForPrisonStaffBetween(
          prisonCode,
          staffMembers.keys.toSet(),
          reportingPeriod.from,
          reportingPeriod.to.plusDays(1),
        ).filterApplicable(reportingPeriod)
        .groupBy { it.staffId }

    val staffDetail =
      if (staffMembers.keys.isEmpty()) {
        emptyMap()
      } else {
        staffConfigRepository.findAllWithAllocationCount(prisonCode, staffMembers.keys).associateBy { it.staffId }
      }

    val staffCaseNoteSummaries =
      if (includeStats) {
        recordedEventRetriever.findRecordedEventSummaries(
          staffMembers.keys,
          reportingPeriod.from.toLocalDate(),
          reportingPeriod.to.toLocalDate(),
        )
      } else {
        emptyMap()
      }

    val activeStatusProvider =
      lazy {
        referenceDataRepository.findByKey(ReferenceDataDomain.STAFF_STATUS of StaffStatus.ACTIVE.name)!!
      }
    return AllocatableSearchResponse(
      staffMembers.values
        .map {
          it.allocatableSummary(
            prisonConfig,
            reportingPeriod,
            { staffId -> staffDetail[staffId] },
            { staffId -> applicableAllocationsByStaff[staffId].orEmpty() },
            { staffId ->
              if (includeStats) {
                staffCaseNoteSummaries[staffId] ?: RecordedEventSummaries.empty()
              } else {
                RecordedEventSummaries.empty()
              }
            },
            activeStatusProvider,
            includeStats,
          )
        }.filter { ss -> (request.status == StaffStatus.ALL || request.status.name == ss.status.code) },
    )
  }

  fun findAllocatableStaff(prisonCode: String): List<AllocatableStaff> {
    val policy = AllocationContext.get().policy
    val (staff, roles) =
      if (policy == AllocationPolicy.KEY_WORKER) {
        val keyworkers = prisonApi.getKeyworkersForPrison(prisonCode)
        val staff = keyworkers.map { StaffSummary(it.staffId, it.firstName, it.lastName) }
        val rd = referenceDataRepository.findAllByKeyIn(keyworkers.flatMap { it.rdKeys() }.toSet()).associateBy { it.key }
        val cd: (ReferenceDataKey) -> CodedDescription = { rdKey -> requireNotNull(rd[rdKey]).asCodedDescription() }
        staff to
          keyworkers.associate {
            it.staffId to
              StaffRoleInfo(
                cd(ReferenceDataDomain.STAFF_POSITION of it.position),
                cd(ReferenceDataDomain.STAFF_SCHEDULE_TYPE of it.scheduleType),
                it.hoursPerWeek,
                it.fromDate,
                it.toDate,
              )
          }
      } else {
        val staffRoles = staffRoleRepository.findAllByPrisonCode(prisonCode).associate { it.staffId to it.roleInfo() }
        val staff = prisonApi.getStaffSummariesFromIds(staffRoles.map { it.key }.toSet())
        staff to staffRoles
      }

    return staff.map { AllocatableStaff(it, requireNotNull(roles[it.staffId])) }
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

  private fun StaffSummary.matches(query: String?): Boolean {
    if (query == null) return true
    val parts = query.split("\\s+".toRegex())
    return parts.all { part ->
      firstName.lowercase().startsWith(part.lowercase()) || lastName.lowercase().startsWith(part.lowercase())
    }
  }

  private fun StaffWithRole.searchResponse(
    staffConfig: (Long) -> StaffWithAllocationCount?,
    prisonConfig: PrisonConfiguration,
    sessions: (Long) -> UsageByAuthorIdResponse?,
    entries: (Long) -> UsageByAuthorIdResponse?,
    activeStatusProvider: Lazy<ReferenceData>,
  ): StaffSearchResult {
    val kwa = staffConfig(staffId)
    val status = kwa?.staffConfig?.status ?: activeStatusProvider.value
    return StaffSearchResult(
      staffId,
      firstName,
      lastName,
      status.asCodedDescription(),
      kwa?.staffConfig?.capacity ?: prisonConfig.maximumCapacity,
      kwa?.allocationCount ?: 0,
      kwa?.staffConfig?.allowAutoAllocation ?: prisonConfig.allowAutoAllocation,
      sessions(staffId)?.count ?: 0,
      entries(staffId)?.count ?: 0,
      staffRole,
      username,
      email,
    )
  }

  private fun AllocatableStaff.allocatableSummary(
    prisonConfig: PrisonConfiguration,
    reportingPeriod: ReportingPeriod,
    staffConfig: (Long) -> StaffWithAllocationCount?,
    applicableAllocations: (Long) -> List<Allocation>,
    cnSummary: (Long) -> RecordedEventSummaries,
    activeStatusProvider: Lazy<ReferenceData>,
    includeStats: Boolean,
  ): AllocatableSummary {
    val config = staffConfig(staffMember.staffId)
    val status = config?.staffConfig?.status ?: activeStatusProvider.value
    return AllocatableSummary(
      staffMember.staffId,
      staffMember.firstName,
      staffMember.lastName,
      status.asCodedDescription(),
      config?.staffConfig?.capacity ?: prisonConfig.maximumCapacity,
      config?.allocationCount ?: 0,
      config?.staffConfig?.allowAutoAllocation ?: prisonConfig.allowAutoAllocation,
      staffRole,
      if (includeStats) {
        applicableAllocations(staffMember.staffId).staffCountStatsFromApplicableAllocations(
          reportingPeriod,
          prisonConfig,
          cnSummary(staffMember.staffId),
        )
      } else {
        null
      },
    )
  }
}

private fun NomisStaffRole.rdKeys() =
  setOf(ReferenceDataDomain.STAFF_POSITION of position, ReferenceDataDomain.STAFF_SCHEDULE_TYPE of scheduleType)

private fun StaffRole.roleInfo() =
  StaffRoleInfo(position.asCodedDescription(), scheduleType.asCodedDescription(), hoursPerWeek, fromDate, toDate)
