package uk.gov.justice.digital.hmpps.keyworker.services

import jakarta.persistence.EntityNotFoundException
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationContext
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationPolicy
import uk.gov.justice.digital.hmpps.keyworker.domain.Allocation
import uk.gov.justice.digital.hmpps.keyworker.domain.PrisonConfiguration
import uk.gov.justice.digital.hmpps.keyworker.domain.PrisonConfigurationRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceDataDomain
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceDataRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.StaffAllocationRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.StaffConfigRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.StaffRoleRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.asCodedDescription
import uk.gov.justice.digital.hmpps.keyworker.domain.filterApplicable
import uk.gov.justice.digital.hmpps.keyworker.domain.of
import uk.gov.justice.digital.hmpps.keyworker.domain.toKeyworkerStatusCodedDescription
import uk.gov.justice.digital.hmpps.keyworker.domain.toModel
import uk.gov.justice.digital.hmpps.keyworker.dto.CodedDescription
import uk.gov.justice.digital.hmpps.keyworker.dto.JobClassificationResponse
import uk.gov.justice.digital.hmpps.keyworker.dto.LatestSession
import uk.gov.justice.digital.hmpps.keyworker.dto.NomisStaffRole
import uk.gov.justice.digital.hmpps.keyworker.dto.ReportingPeriod
import uk.gov.justice.digital.hmpps.keyworker.dto.StaffCountStats
import uk.gov.justice.digital.hmpps.keyworker.dto.StaffDetails
import uk.gov.justice.digital.hmpps.keyworker.dto.StaffRoleInfo
import uk.gov.justice.digital.hmpps.keyworker.dto.StaffStats
import uk.gov.justice.digital.hmpps.keyworker.dto.StaffSummary
import uk.gov.justice.digital.hmpps.keyworker.integration.PrisonApiClient
import uk.gov.justice.digital.hmpps.keyworker.integration.Prisoner
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNoteSummary
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNoteSummary.Companion.emptyEvents
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNotesApiClient
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.UsageByPersonIdentifierRequest.Companion.keyworkerTypes
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.UsageByPersonIdentifierRequest.Companion.personalOfficerTypes
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.summary
import uk.gov.justice.digital.hmpps.keyworker.integration.getRelevantAlertCodes
import uk.gov.justice.digital.hmpps.keyworker.integration.getRemainingAlertCount
import uk.gov.justice.digital.hmpps.keyworker.integration.prisonersearch.PrisonerSearchClient
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit.DAYS
import kotlin.math.floor
import uk.gov.justice.digital.hmpps.keyworker.dto.Allocation as AllocationModel
import uk.gov.justice.digital.hmpps.keyworker.dto.Prisoner as Person

@Service
class GetStaffDetails(
  private val prisonApi: PrisonApiClient,
  private val staffRoleRepository: StaffRoleRepository,
  private val prisonConfigRepository: PrisonConfigurationRepository,
  private val staffConfigRepository: StaffConfigRepository,
  private val allocationRepository: StaffAllocationRepository,
  private val prisonerSearch: PrisonerSearchClient,
  private val prisonRegisterApi: PrisonRegisterClient,
  private val caseNotesApiClient: CaseNotesApiClient,
  private val referenceDataRepository: ReferenceDataRepository,
) {
  fun getJobClassificationsFor(
    prisonCode: String,
    staffId: Long,
  ): JobClassificationResponse {
    val policies: Set<AllocationPolicy> =
      buildSet {
        if (prisonApi.getKeyworkerForPrison(prisonCode, staffId)?.takeIf { !it.isExpired() } != null) {
          add(
            AllocationPolicy.KEY_WORKER,
          )
        }
        addAll(
          staffRoleRepository
            .findByPrisonCodeAndStaffIdAndPolicyIn(
              prisonCode,
              staffId,
            ).mapNotNull {
              AllocationPolicy.of(it.policy)
            },
        )
      }
    return JobClassificationResponse(policies)
  }

  fun getDetailsFor(
    prisonCode: String,
    staffId: Long,
    from: LocalDate?,
    to: LocalDate?,
    includeStats: Boolean,
  ): StaffDetails {
    val reportingPeriod =
      if (from != null && to != null) {
        ReportingPeriod(from.atStartOfDay(), to.plusDays(1).atStartOfDay())
      } else {
        ReportingPeriod.currentMonth()
      }

    val context = AllocationContext.get()
    val staffWithRole =
      if (context.policy == AllocationPolicy.KEY_WORKER) {
        prisonApi.getKeyworkerForPrison(prisonCode, staffId)?.staffWithRole()
      } else {
        prisonApi.findStaffSummariesFromIds(setOf(staffId)).firstOrNull()?.let {
          it to staffRoleRepository.findByPrisonCodeAndStaffId(prisonCode, staffId)?.toModel()
        }
      }
    if (staffWithRole?.first == null) {
      throw EntityNotFoundException("Staff member not found")
    }

    val prisonConfig = prisonConfigRepository.findByCode(prisonCode) ?: PrisonConfiguration.default(prisonCode)

    val staffInfo = staffConfigRepository.findAllWithAllocationCount(prisonCode, setOf(staffId)).firstOrNull()
    val allAllocations =
      allocationRepository.findActiveForPrisonStaffBetween(
        prisonCode,
        staffId,
        reportingPeriod.from,
        reportingPeriod.to,
      )
    val activeAllocations = allAllocations.filter { it.isActive }
    val prisonerDetails =
      if (activeAllocations.isEmpty()) {
        emptyMap()
      } else {
        prisonerSearch
          .findPrisonerDetails(activeAllocations.map { it.personIdentifier }.toSet())
          .filter { it.prisonId == prisonCode }
          .associateBy { it.prisonerNumber }
      }
    val prisonName =
      prisonerDetails.values.firstOrNull()?.prisonName ?: prisonRegisterApi.findPrison(prisonCode)!!.prisonName

    val (allocations, stats) =
      if (includeStats) {
        val (current, cnSummary) = allAllocations.staffCountStats(reportingPeriod, prisonConfig, staffId)
        val (previous, _) =
          allAllocations.staffCountStats(reportingPeriod.previousPeriod(), prisonConfig, staffId)
        val allocations =
          activeAllocations
            .mapNotNull { alloc ->
              prisonerDetails[alloc.personIdentifier]?.let {
                alloc.asAllocation(it, reportingPeriod, prisonConfig, cnSummary)
              }
            }.sortedWith(compareBy({ it.prisoner.lastName }, { it.prisoner.firstName }))
        allocations to StaffStats(current, previous)
      } else {
        null to null
      }

    val staff = staffWithRole.first
    return StaffDetails(
      staff.staffId,
      staff.firstName,
      staff.lastName,
      staffInfo?.staffConfig?.status.toKeyworkerStatusCodedDescription(),
      CodedDescription(prisonCode, prisonName),
      staffInfo?.staffConfig?.capacity ?: prisonConfig.capacity,
      staffInfo?.allocationCount ?: 0,
      allocations ?: emptyList(),
      stats,
      staffInfo?.staffConfig?.allowAutoAllocation ?: prisonConfig.allowAutoAllocation,
      staffInfo?.staffConfig?.reactivateOn,
      staffWithRole.second,
    )
  }

  private fun NomisStaffRole.staffWithRole(): Pair<StaffSummary, StaffRoleInfo> =
    StaffSummary(staffId, firstName, lastName) to staffRoleInfo()

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

  private fun List<Allocation>.staffCountStats(
    reportingPeriod: ReportingPeriod,
    prisonConfig: PrisonConfiguration,
    staffId: Long,
  ): Pair<StaffCountStats, CaseNoteSummary?> {
    val context = AllocationContext.get()
    val applicableAllocations = filterApplicable(reportingPeriod)
    val personIdentifiers = applicableAllocations.map { it.personIdentifier }.toSet()
    val cnSummary =
      if (personIdentifiers.isEmpty()) {
        null
      } else {
        caseNotesApiClient
          .getUsageByPersonIdentifier(
            if (context.policy == AllocationPolicy.KEY_WORKER) {
              keyworkerTypes(
                prisonConfig.code,
                personIdentifiers,
                reportingPeriod.from,
                reportingPeriod.to,
                setOf(staffId.toString()),
              )
            } else {
              personalOfficerTypes(
                prisonConfig.code,
                personIdentifiers,
                reportingPeriod.from,
                reportingPeriod.to,
                setOf(staffId.toString()),
              )
            },
          ).summary()
      }

    return Pair(
      applicableAllocations.staffCountStatsFromApplicableAllocations(reportingPeriod, prisonConfig, cnSummary),
      cnSummary,
    )
  }
}

fun List<Allocation>.staffCountStatsFromApplicableAllocations(
  reportingPeriod: ReportingPeriod,
  prisonConfig: PrisonConfiguration,
  cnSummary: CaseNoteSummary?,
): StaffCountStats {
  val context = AllocationContext.get()
  val projectedSessions =
    if (count() > 0) {
      val sessionPerDay = 1 / (prisonConfig.frequencyInWeeks * 7.0)
      sumOf {
        floor(it.daysAllocatedForStats(reportingPeriod) * sessionPerDay + 0.5).toInt()
      }
    } else {
      0
    }
  val compliance =
    if (projectedSessions == 0 || cnSummary == null) {
      null
    } else {
      Statistic.percentage(
        cnSummary.totalComplianceEvents(context.policy),
        projectedSessions,
      )
    }

  return StaffCountStats(
    reportingPeriod.from.toLocalDate(),
    reportingPeriod.to.toLocalDate().minusDays(1),
    projectedSessions,
    cnSummary?.totalComplianceEvents(context.policy) ?: 0,
    cnSummary?.countEvents(context.policy) ?: emptyEvents(context.policy),
    compliance ?: 0.0,
  )
}

private fun Prisoner.asPrisoner() =
  Person(
    prisonerNumber,
    firstName,
    lastName,
    csra,
    cellLocation,
    releaseDate,
    alerts.getRelevantAlertCodes(),
    alerts.getRemainingAlertCount(),
  )

private fun Allocation.asAllocation(
  prisoner: Prisoner,
  reportingPeriod: ReportingPeriod,
  prisonConfig: PrisonConfiguration,
  cnSummary: CaseNoteSummary?,
) = AllocationModel(
  prisoner.asPrisoner(),
  listOf(
    this,
  ).filterApplicable(
    reportingPeriod,
  ).staffCountStatsFromApplicableAllocations(
    reportingPeriod,
    prisonConfig,
    cnSummary?.filterByPrisonerNumber(prisoner.prisonerNumber),
  ),
  cnSummary?.findSessionDate(prisoner.prisonerNumber)?.let { LatestSession(it) },
)

fun Allocation.daysAllocatedForStats(reportingPeriod: ReportingPeriod): Long {
  val endTime: LocalDateTime = deallocatedAt ?: reportingPeriod.to
  val startTime: LocalDateTime = maxOf(allocatedAt, reportingPeriod.from)
  return DAYS.between(startTime, endTime)
}
