package uk.gov.justice.digital.hmpps.keyworker.statistics

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationContext
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationContextHolder
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationPolicy
import uk.gov.justice.digital.hmpps.keyworker.domain.AllocationRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.PrisonConfigurationRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.PrisonStatistic
import uk.gov.justice.digital.hmpps.keyworker.domain.PrisonStatisticRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.StaffConfigRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.StaffRoleRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.getNonActiveStaff
import uk.gov.justice.digital.hmpps.keyworker.events.ComplexityOfNeedLevel.HIGH
import uk.gov.justice.digital.hmpps.keyworker.integration.PrisonApiClient
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNoteSummary
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNotesApiClient
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.UsageByPersonIdentifierRequest
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.UsageByPersonIdentifierRequest.Companion.keyworkerTypes
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.UsageByPersonIdentifierRequest.Companion.personalOfficerTypes
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.UsageByPersonIdentifierRequest.Companion.sessionTypes
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.summary
import uk.gov.justice.digital.hmpps.keyworker.integration.complexityofneed.ComplexityOfNeedApiClient
import uk.gov.justice.digital.hmpps.keyworker.integration.events.HmppsDomainEvent
import uk.gov.justice.digital.hmpps.keyworker.integration.events.PrisonStatisticsInfo
import uk.gov.justice.digital.hmpps.keyworker.integration.prisonersearch.PrisonerSearchClient
import java.time.LocalDate
import java.time.format.DateTimeFormatter.ISO_LOCAL_DATE
import java.time.temporal.ChronoUnit.DAYS

@Service
class PrisonStatisticCalculator(
  private val contextHolder: AllocationContextHolder,
  private val statisticRepository: PrisonStatisticRepository,
  private val prisonerSearch: PrisonerSearchClient,
  private val complexityOfNeedApi: ComplexityOfNeedApiClient,
  private val allocationRepository: AllocationRepository,
  private val caseNotesApi: CaseNotesApiClient,
  private val prisonApi: PrisonApiClient,
  private val prisonConfigRepository: PrisonConfigurationRepository,
  private val staffConfigRepository: StaffConfigRepository,
  private val staffRoleRepository: StaffRoleRepository,
  private val telemetryClient: TelemetryClient,
) {
  fun calculate(info: HmppsDomainEvent<PrisonStatisticsInfo>) {
    with(info.additionalInformation) {
      contextHolder.setContext(AllocationContext.get().copy(policy = policy))
      val stats = statisticRepository.findByPrisonCodeAndDate(prisonCode, date)

      if (stats != null) return
      val prisoners = prisonerSearch.findAllPrisoners(prisonCode)

      if (prisoners.isEmpty()) {
        telemetryClient.trackEvent(
          "EmptyPrison",
          mapOf("prisonCode" to prisonCode, "date" to ISO_LOCAL_DATE.format(date)),
          null,
        )
        return
      }

      val prisonConfig = prisonConfigRepository.findByCode(prisonCode)
      val complexityOfNeed =
        if (prisonConfig?.hasPrisonersWithHighComplexityNeeds == true) {
          complexityOfNeedApi.getComplexityOfNeed(prisoners.personIdentifiers()).associateBy { it.personIdentifier }
        } else {
          emptyMap()
        }
      val prisonersWithComplexNeeds =
        complexityOfNeed.values
          .filter { it.level == HIGH }
          .map { it.personIdentifier }
          .toSet()
      val eligiblePrisoners = prisoners.personIdentifiers() - prisonersWithComplexNeeds

      if (eligiblePrisoners.isEmpty()) {
        telemetryClient.trackEvent(
          "NoEligiblePrisoners",
          mapOf(
            "prisonCode" to prisonCode,
            "date" to ISO_LOCAL_DATE.format(date),
            "totalPrisoners" to prisoners.size.toString(),
            "withComplexNeeds" to prisonersWithComplexNeeds.size.toString(),
          ),
          null,
        )
        return
      }

      val activeAllocations = allocationRepository.countActiveAllocations(prisonCode, eligiblePrisoners)
      val newAllocations =
        allocationRepository
          .findNewAllocationsAt(prisonCode, date, date.plusDays(1), policy.name)
          .associateBy { it.personIdentifier }

      val cnSummaryTypes = getCnSummaryTypes(eligiblePrisoners)
      val cnSummary = caseNotesApi.getUsageByPersonIdentifier(cnSummaryTypes).summary()
      val peopleWithRecordedEntries = cnSummary.personIdentifiersWithSessions()
      val previousRecordedEntries = getPreviousSessions(peopleWithRecordedEntries)
      val activeStaffCount = getActiveStaffCount()

      val summaries =
        PeopleSummaries(
          eligiblePrisoners,
          {
            listOfNotNull(
              prisoners[it]?.lastAdmissionDate,
              complexityOfNeed[it]?.updatedTimeStamp?.toLocalDate(),
            ).maxOrNull()
          },
          { newAllocations[it]?.assignedAt?.toLocalDate() },
          { pi ->
            cnSummary
              .findLatestForPolicy(pi, policy)
              ?.takeIf { previousRecordedEntries?.findLatestForPolicy(pi, policy) == null }
          },
        )

      val overSixMonths =
        summaries.data.filter {
          val sixMonthsInDays = DAYS.between(LocalDate.now().minusMonths(6), LocalDate.now())
          (it.eligibilityToAllocationInDays ?: 0) > sixMonthsInDays ||
            (it.eligibilityToRecordedEntryInDays ?: 0) > sixMonthsInDays
        }

      if (overSixMonths.isNotEmpty()) {
        telemetryClient.trackEvent(
          "OverSixMonths",
          overSixMonths.associate {
            it.personIdentifier to ISO_LOCAL_DATE.format(it.eligibilityDate!!)
            "policy" to policy.name
          },
          mapOf(),
        )
      }

      statisticRepository.save(
        PrisonStatistic(
          prisonCode = prisonCode,
          date = date,
          prisonerCount = prisoners.size,
          highComplexityOfNeedPrisonerCount = prisonersWithComplexNeeds.size,
          eligiblePrisonerCount = eligiblePrisoners.size,
          prisonersAssignedCount = activeAllocations,
          eligibleStaffCount = activeStaffCount,
          recordedSessionCount = cnSummary.keyworkerSessions,
          recordedEntryCount = if (policy == AllocationPolicy.KEY_WORKER) cnSummary.keyworkerEntries else cnSummary.poEntries,
          receptionToAllocationDays = summaries.averageDaysToAllocation,
          receptionToRecordedEventDays = summaries.averageDaysToRecordedEntry,
        ),
      )
    }
  }

  private fun PrisonStatisticsInfo.getCnSummaryTypes(eligiblePrisoners: Set<String>): UsageByPersonIdentifierRequest =
    if (policy == AllocationPolicy.KEY_WORKER) {
      keyworkerTypes(prisonCode, eligiblePrisoners, date.atStartOfDay())
    } else {
      personalOfficerTypes(prisonCode, eligiblePrisoners, date.atStartOfDay())
    }

  private fun PrisonStatisticsInfo.getPreviousSessions(peopleWithSessions: Set<String>): CaseNoteSummary? {
    if (peopleWithSessions.isEmpty()) return null
    return when (policy) {
      AllocationPolicy.KEY_WORKER ->
        caseNotesApi
          .getUsageByPersonIdentifier(
            sessionTypes(prisonCode, peopleWithSessions, date.minusMonths(6), date.minusDays(1)),
          ).summary()

      AllocationPolicy.PERSONAL_OFFICER ->
        caseNotesApi
          .getUsageByPersonIdentifier(
            personalOfficerTypes(
              prisonCode,
              peopleWithSessions,
              date.minusMonths(6).atStartOfDay(),
              date.minusDays(1).atStartOfDay(),
            ),
          ).summary()
    }
  }

  private fun PrisonStatisticsInfo.getActiveStaffCount(): Int {
    val potentiallyActiveStaffIds =
      if (policy == AllocationPolicy.KEY_WORKER) {
        prisonApi.getKeyworkersForPrison(prisonCode).map { it.staffId }.toSet()
      } else {
        staffRoleRepository.findAllByPrisonCode(prisonCode).map { it.staffId }.toSet()
      }
    val inactiveIds =
      staffConfigRepository
        .getNonActiveStaff(potentiallyActiveStaffIds)
        .map { it.staffId }
        .toSet()

    return (potentiallyActiveStaffIds - inactiveIds).size
  }
}

class PeopleSummaries(
  personIdentifiers: Set<String>,
  getReceptionDate: (String) -> LocalDate?,
  getAllocationDate: (String) -> LocalDate?,
  getRecordedEntryDate: (String) -> LocalDate?,
) {
  val data =
    personIdentifiers.map {
      PersonSummary(
        it,
        getReceptionDate(it),
        getAllocationDate(it),
        getRecordedEntryDate(it),
      )
    }

  private fun PersonSummary.eligibilityDateIsValid() = eligibilityDate?.isAfter(LocalDate.now().minusMonths(6)) ?: false

  private val allocationDays =
    data.mapNotNull { if (it.eligibilityDateIsValid()) it.eligibilityToAllocationInDays else null }
  private val recordedEntryDays = data.mapNotNull { if (it.eligibilityDateIsValid()) it.eligibilityToRecordedEntryInDays else null }

  val averageDaysToAllocation = if (allocationDays.isEmpty()) null else allocationDays.average().toInt()
  val averageDaysToRecordedEntry = if (recordedEntryDays.isEmpty()) null else recordedEntryDays.average().toInt()
}

data class PersonSummary(
  val personIdentifier: String,
  val eligibilityDate: LocalDate?,
  val allocationDate: LocalDate?,
  val recordedEntryDate: LocalDate?,
) {
  val eligibilityToAllocationInDays =
    if (eligibilityDate != null && allocationDate != null && allocationDate >= eligibilityDate) {
      DAYS.between(eligibilityDate, allocationDate).toInt()
    } else {
      null
    }
  val eligibilityToRecordedEntryInDays =
    if (eligibilityDate != null && recordedEntryDate != null && recordedEntryDate >= eligibilityDate) {
      DAYS.between(eligibilityDate, recordedEntryDate).toInt()
    } else {
      null
    }
}
