package uk.gov.justice.digital.hmpps.keyworker.services.staff

import jakarta.persistence.EntityNotFoundException
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationContext
import uk.gov.justice.digital.hmpps.keyworker.domain.Allocation
import uk.gov.justice.digital.hmpps.keyworker.domain.AllocationRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.PrisonConfiguration
import uk.gov.justice.digital.hmpps.keyworker.domain.PrisonConfigurationRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.StaffConfigRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.filterApplicable
import uk.gov.justice.digital.hmpps.keyworker.domain.toKeyworkerStatusCodedDescription
import uk.gov.justice.digital.hmpps.keyworker.integration.prisonersearch.Prisoner
import uk.gov.justice.digital.hmpps.keyworker.integration.prisonersearch.PrisonerSearchClient
import uk.gov.justice.digital.hmpps.keyworker.integration.prisonersearch.getRelevantAlertCodes
import uk.gov.justice.digital.hmpps.keyworker.integration.prisonersearch.getRemainingAlertCount
import uk.gov.justice.digital.hmpps.keyworker.integration.prisonregister.PrisonRegisterClient
import uk.gov.justice.digital.hmpps.keyworker.model.CodedDescription
import uk.gov.justice.digital.hmpps.keyworker.model.ReportingPeriod
import uk.gov.justice.digital.hmpps.keyworker.model.staff.JobClassificationResponse
import uk.gov.justice.digital.hmpps.keyworker.model.staff.LatestSession
import uk.gov.justice.digital.hmpps.keyworker.model.staff.StaffCountStats
import uk.gov.justice.digital.hmpps.keyworker.model.staff.StaffDetails
import uk.gov.justice.digital.hmpps.keyworker.model.staff.StaffStats
import uk.gov.justice.digital.hmpps.keyworker.services.Statistic
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
  staffRoleRetrievers: List<StaffRoleRetriever>,
  private val prisonConfigRepository: PrisonConfigurationRepository,
  private val staffConfigRepository: StaffConfigRepository,
  private val allocationRepository: AllocationRepository,
  private val prisonerSearch: PrisonerSearchClient,
  private val prisonRegisterApi: PrisonRegisterClient,
  private val caseNotesRetriever: RecordedEventRetriever,
) {
  private val staffRoleRetriever = staffRoleRetrievers.flatMap { it.policies.map { policy -> policy to it } }.toMap()

  fun getJobClassificationsFor(
    prisonCode: String,
    staffId: Long,
  ): JobClassificationResponse =
    JobClassificationResponse(
      staffRoleRetriever.values
        .flatMap { it.getActivePoliciesForPrison(prisonCode, staffId) }
        .toSet(),
    )

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

    val policy = AllocationContext.get().requiredPolicy()
    val staffWithRole =
      staffRoleRetriever[policy]?.getStaffWithRole(prisonCode, staffId)
        ?: throw EntityNotFoundException("Staff member not found")

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

    val staff = staffWithRole.staff
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
      staffWithRole.role,
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
