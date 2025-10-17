package uk.gov.justice.digital.hmpps.keyworker.services.staff

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationContext
import uk.gov.justice.digital.hmpps.keyworker.domain.Allocation
import uk.gov.justice.digital.hmpps.keyworker.domain.AllocationRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.PrisonConfiguration
import uk.gov.justice.digital.hmpps.keyworker.domain.PrisonConfigurationRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceData
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceDataDomain
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceDataRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.StaffConfigRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.StaffWithAllocationCount
import uk.gov.justice.digital.hmpps.keyworker.domain.asCodedDescription
import uk.gov.justice.digital.hmpps.keyworker.domain.filterApplicable
import uk.gov.justice.digital.hmpps.keyworker.domain.of
import uk.gov.justice.digital.hmpps.keyworker.integration.nomisuserroles.NomisUserRolesApiClient
import uk.gov.justice.digital.hmpps.keyworker.model.ReportingPeriod
import uk.gov.justice.digital.hmpps.keyworker.model.staff.AllocatableSearchRequest
import uk.gov.justice.digital.hmpps.keyworker.model.staff.AllocatableSearchResponse
import uk.gov.justice.digital.hmpps.keyworker.model.staff.AllocatableSummary
import uk.gov.justice.digital.hmpps.keyworker.model.staff.StaffSearchRequest
import uk.gov.justice.digital.hmpps.keyworker.model.staff.StaffSearchResponse
import uk.gov.justice.digital.hmpps.keyworker.model.staff.StaffSearchResult
import uk.gov.justice.digital.hmpps.keyworker.model.staff.StaffStatus
import uk.gov.justice.digital.hmpps.keyworker.model.staff.StaffSummary
import uk.gov.justice.digital.hmpps.keyworker.model.staff.StaffWithRole
import uk.gov.justice.digital.hmpps.keyworker.services.recordedevents.RecordedEventRetriever
import uk.gov.justice.digital.hmpps.keyworker.services.recordedevents.RecordedEventSummaries

@Service
class StaffSearch(
  staffRoleRetrievers: List<StaffRoleRetriever>,
  private val nomisUsersApi: NomisUserRolesApiClient,
  private val recordedEventRetriever: RecordedEventRetriever,
  private val allocationRepository: AllocationRepository,
  private val prisonConfigRepository: PrisonConfigurationRepository,
  private val staffConfigRepository: StaffConfigRepository,
  private val referenceDataRepository: ReferenceDataRepository,
) {
  private val staffRoleRetriever = staffRoleRetrievers.flatMap { it.policies.map { policy -> policy to it } }.toMap()

  fun searchForStaff(
    prisonCode: String,
    request: StaffSearchRequest,
  ): StaffSearchResponse {
    val staffMembers = searchStaff(prisonCode, request.query)
    val staffIds = staffMembers.map { it.staffId }.toSet()

    val currentMonth = ReportingPeriod.currentMonth()
    val recordedEvents =
      recordedEventRetriever.findRecordedEventSummaries(
        prisonCode,
        staffIds,
        currentMonth.from.toLocalDate(),
        currentMonth.to.toLocalDate(),
      )

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
            { staffId -> recordedEvents[staffId]?.sessionCount },
            { staffId -> recordedEvents[staffId]?.entryCount },
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
    val policy = AllocationContext.get().requiredPolicy()
    val staffMembers = nomisUsersApi.getUsers(prisonCode, nameFilter).content
    val roleInfo = requireNotNull(staffRoleRetriever[policy]).getStaffRoles(prisonCode)
    return staffMembers
      .filter { it.staffStatus == StaffStatus.ACTIVE.name }
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
        .filter { it.staff.matches(request.query) }
        .associateBy { it.staff.staffId }

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
          prisonCode,
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

  fun findAllocatableStaff(prisonCode: String): List<StaffSummaryWithRole> {
    val policy = AllocationContext.get().requiredPolicy()
    return requireNotNull(staffRoleRetriever[policy]).getStaffWithRoles(prisonCode)
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
    sessions: (Long) -> Int?,
    entries: (Long) -> Int?,
    activeStatusProvider: Lazy<ReferenceData>,
  ): StaffSearchResult {
    val staffConfig = staffConfig(staffId)
    val status = staffConfig?.staffConfig?.status ?: activeStatusProvider.value
    return StaffSearchResult(
      staffId,
      firstName,
      lastName,
      status.asCodedDescription(),
      staffConfig?.staffConfig?.capacity ?: prisonConfig.capacity,
      staffConfig?.allocationCount ?: 0,
      sessions(staffId) ?: 0,
      entries(staffId) ?: 0,
      staffRole,
      username,
      email,
    )
  }

  private fun StaffSummaryWithRole.allocatableSummary(
    prisonConfig: PrisonConfiguration,
    reportingPeriod: ReportingPeriod,
    staffConfig: (Long) -> StaffWithAllocationCount?,
    applicableAllocations: (Long) -> List<Allocation>,
    reSummary: (Long) -> RecordedEventSummaries,
    activeStatusProvider: Lazy<ReferenceData>,
    includeStats: Boolean,
  ): AllocatableSummary {
    val config = staffConfig(staff.staffId)
    val status = config?.staffConfig?.status ?: activeStatusProvider.value
    return AllocatableSummary(
      staff.staffId,
      staff.firstName,
      staff.lastName,
      status.asCodedDescription(),
      config?.staffConfig?.capacity ?: prisonConfig.capacity,
      config?.allocationCount ?: 0,
      role,
      if (includeStats) {
        applicableAllocations(staff.staffId).staffCountStatsFromApplicableAllocations(
          reportingPeriod,
          prisonConfig,
          reSummary(staff.staffId),
        )
      } else {
        null
      },
    )
  }
}
