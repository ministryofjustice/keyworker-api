package uk.gov.justice.digital.hmpps.keyworker.services

import jakarta.persistence.EntityNotFoundException
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationContext
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationPolicy
import uk.gov.justice.digital.hmpps.keyworker.config.set
import uk.gov.justice.digital.hmpps.keyworker.domain.Allocation
import uk.gov.justice.digital.hmpps.keyworker.domain.AllocationRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.PrisonConfiguration
import uk.gov.justice.digital.hmpps.keyworker.domain.PrisonConfigurationRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceDataDomain
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceDataRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.StaffConfigRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.StaffRoleRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.asCodedDescription
import uk.gov.justice.digital.hmpps.keyworker.domain.filterApplicable
import uk.gov.justice.digital.hmpps.keyworker.domain.of
import uk.gov.justice.digital.hmpps.keyworker.domain.toKeyworkerStatusCodedDescription
import uk.gov.justice.digital.hmpps.keyworker.domain.toModel
import uk.gov.justice.digital.hmpps.keyworker.integration.prisonapi.NomisStaffRole
import uk.gov.justice.digital.hmpps.keyworker.integration.prisonapi.PrisonApiClient
import uk.gov.justice.digital.hmpps.keyworker.integration.prisonersearch.Prisoner
import uk.gov.justice.digital.hmpps.keyworker.integration.prisonersearch.PrisonerSearchClient
import uk.gov.justice.digital.hmpps.keyworker.integration.prisonersearch.getRelevantAlertCodes
import uk.gov.justice.digital.hmpps.keyworker.integration.prisonersearch.getRemainingAlertCount
import uk.gov.justice.digital.hmpps.keyworker.model.CodedDescription
import uk.gov.justice.digital.hmpps.keyworker.model.ReportingPeriod
import uk.gov.justice.digital.hmpps.keyworker.model.staff.JobClassificationResponse
import uk.gov.justice.digital.hmpps.keyworker.model.staff.LatestSession
import uk.gov.justice.digital.hmpps.keyworker.model.staff.StaffCountStats
import uk.gov.justice.digital.hmpps.keyworker.model.staff.StaffDetails
import uk.gov.justice.digital.hmpps.keyworker.model.staff.StaffRoleInfo
import uk.gov.justice.digital.hmpps.keyworker.model.staff.StaffStats
import uk.gov.justice.digital.hmpps.keyworker.model.staff.StaffSummary
import uk.gov.justice.digital.hmpps.keyworker.services.recordedevents.RecordedEventRetriever
import uk.gov.justice.digital.hmpps.keyworker.services.recordedevents.RecordedEventSummaries
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit.DAYS
import kotlin.math.floor
import uk.gov.justice.digital.hmpps.keyworker.model.staff.Allocation as AllocationModel
import uk.gov.justice.digital.hmpps.keyworker.model.staff.Prisoner as Person

@Service
class GetStaffDetails(
  private val prisonApi: PrisonApiClient,
  private val staffRoleRepository: StaffRoleRepository,
  private val prisonConfigRepository: PrisonConfigurationRepository,
  private val staffConfigRepository: StaffConfigRepository,
  private val allocationRepository: AllocationRepository,
  private val prisonerSearch: PrisonerSearchClient,
  private val prisonRegisterApi: PrisonRegisterClient,
  private val caseNotesRetriever: RecordedEventRetriever,
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
        addAll(staffRoleRepository.findActiveStaffPoliciesForPrison(prisonCode, staffId))
      }
    return JobClassificationResponse(policies)
  }

  fun getDetailsFor(
    prisonCode: String,
    staffId: Long,
    from: LocalDate?,
    to: LocalDate?,
    comparisonFrom: LocalDate?,
    comparisonTo: LocalDate?,
  ): StaffDetails {
    val includeStats = from != null && to != null && comparisonFrom != null && comparisonTo != null
    val reportingPeriod =
      ReportingPeriod.of(from, to, ReportingPeriod.of(comparisonFrom, comparisonTo)) ?: ReportingPeriod.currentMonth()

    val context = AllocationContext.get()
    val staffWithRole =
      if (context.policy == AllocationPolicy.KEY_WORKER) {
        prisonApi.getKeyworkerForPrison(prisonCode, staffId)?.staffWithRole()
      } else {
        prisonApi.getStaffSummariesFromIds(setOf(staffId)).firstOrNull()?.let {
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
        setOf(staffId),
        reportingPeriod.from,
        reportingPeriod.to.plusDays(1),
      )
    val activeAllocations =
      if (reportingPeriod.to.toLocalDate().isEqual(LocalDate.now())) {
        allAllocations.filter { it.isActive }
      } else {
        allocationRepository.findActiveForPrisonStaff(prisonCode, staffId)
      }
    val prisonerDetails =
      prisonerSearch
        .findPrisonerDetails(activeAllocations.map { it.personIdentifier }.toSet())
        .filter { it.prisonId == prisonCode }
        .associateBy { it.prisonerNumber }

    val prisonName =
      prisonerDetails.values.firstOrNull()?.prisonName ?: prisonRegisterApi.findPrison(prisonCode)!!.prisonName

    val (allocations, stats) =
      if (includeStats) {
        val (current, cnSummaries) = allAllocations.staffCountStats(reportingPeriod, prisonConfig, staffId)
        val (previous, _) = allAllocations.staffCountStats(reportingPeriod.previousPeriod(), prisonConfig, staffId)
        val allocations =
          activeAllocations
            .mapNotNull { alloc ->
              prisonerDetails[alloc.personIdentifier]?.let {
                alloc.asAllocation(it, reportingPeriod, prisonConfig, cnSummaries)
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
  ): Pair<StaffCountStats, RecordedEventSummaries> {
    val applicableAllocations = filterApplicable(reportingPeriod)
    val cns =
      caseNotesRetriever.findRecordedEventSummaries(
        prisonConfig.code,
        setOf(staffId),
        reportingPeriod.from.toLocalDate(),
        reportingPeriod.to.toLocalDate(),
      )[staffId] ?: RecordedEventSummaries.empty()

    return Pair(
      applicableAllocations.staffCountStatsFromApplicableAllocations(reportingPeriod, prisonConfig, cns),
      cns,
    )
  }
}

fun List<Allocation>.staffCountStatsFromApplicableAllocations(
  reportingPeriod: ReportingPeriod,
  prisonConfig: PrisonConfiguration,
  cns: RecordedEventSummaries,
): StaffCountStats {
  val projectedSessions =
    if (isNotEmpty()) {
      val sessionPerDay = 1 / (prisonConfig.frequencyInWeeks * 7.0)
      sumOf {
        floor(it.daysAllocatedForStats(reportingPeriod) * sessionPerDay + 0.5).toInt()
      }
    } else {
      0
    }
  val compliance =
    if (projectedSessions == 0) {
      null
    } else {
      Statistic.percentage(cns.complianceCount, projectedSessions)
    }

  return StaffCountStats(
    reportingPeriod.from.toLocalDate(),
    reportingPeriod.to.toLocalDate(),
    projectedSessions,
    cns.complianceCount,
    cns.recordedEventCount(),
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
  cns: RecordedEventSummaries,
) = AllocationModel(
  prisoner.asPrisoner(),
  listOf(
    this,
  ).filterApplicable(
    reportingPeriod,
  ).staffCountStatsFromApplicableAllocations(
    reportingPeriod,
    prisonConfig,
    cns.findByPersonIdentifier(prisoner.prisonerNumber),
  ),
  cns
    .findLatestRecordedEvent(prisoner.prisonerNumber)
    ?.let { LatestSession(it.occurredAt.toLocalDate()) },
)

fun Allocation.daysAllocatedForStats(reportingPeriod: ReportingPeriod): Long {
  val endTime: LocalDateTime = deallocatedAt ?: reportingPeriod.to
  val startTime: LocalDateTime = maxOf(allocatedAt, reportingPeriod.from)
  return DAYS.between(startTime, endTime)
}
