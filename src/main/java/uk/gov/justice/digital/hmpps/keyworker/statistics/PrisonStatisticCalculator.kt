package uk.gov.justice.digital.hmpps.keyworker.statistics

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
import uk.gov.justice.digital.hmpps.keyworker.integration.complexityofneed.ComplexityOfNeedApiClient
import uk.gov.justice.digital.hmpps.keyworker.integration.events.HmppsDomainEvent
import uk.gov.justice.digital.hmpps.keyworker.integration.events.PrisonStatisticsInfo
import uk.gov.justice.digital.hmpps.keyworker.integration.prisonersearch.PrisonerSearchClient
import uk.gov.justice.digital.hmpps.keyworker.services.casenotes.CaseNoteRetriever
import java.time.LocalDate
import java.time.temporal.ChronoUnit.DAYS

@Service
class PrisonStatisticCalculator(
  private val contextHolder: AllocationContextHolder,
  private val statisticRepository: PrisonStatisticRepository,
  private val prisonerSearch: PrisonerSearchClient,
  private val complexityOfNeedApi: ComplexityOfNeedApiClient,
  private val allocationRepository: AllocationRepository,
  private val caseNoteRetriever: CaseNoteRetriever,
  private val prisonApi: PrisonApiClient,
  private val prisonConfigRepository: PrisonConfigurationRepository,
  private val staffConfigRepository: StaffConfigRepository,
  private val staffRoleRepository: StaffRoleRepository,
) {
  fun calculate(info: HmppsDomainEvent<PrisonStatisticsInfo>) {
    with(info.additionalInformation) {
      contextHolder.setContext(AllocationContext.get().copy(policy = policy))
      val stats = statisticRepository.findByPrisonCodeAndDate(prisonCode, date)

      if (stats != null) return
      val prisoners = prisonerSearch.findAllPrisoners(prisonCode)

      if (prisoners.isEmpty()) {
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
        return
      }

      val activeAllocations = allocationRepository.countActiveAllocations(prisonCode, eligiblePrisoners)
      val newAllocations =
        allocationRepository
          .findNewAllocationsAt(prisonCode, date, date.plusDays(1), policy.name)
          .associateBy { it.personIdentifier }

      val cnSummaries = caseNoteRetriever.findCaseNoteSummaries(prisonCode, date, date)
      val previousRecordedEntries =
        caseNoteRetriever.findMostRecentCaseNoteBefore(prisonCode, cnSummaries.personIdentifiers(), date)

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
          { pi, date ->
            cnSummaries
              .findLatestCaseNote(pi)
              ?.occurredAt
              ?.toLocalDate()
              ?.takeIf {
                val previous = previousRecordedEntries[pi]?.occurredAt?.toLocalDate()
                previous == null || date?.isAfter(previous) == true
              }
          },
        )

      statisticRepository.save(
        PrisonStatistic(
          prisonCode = prisonCode,
          date = date,
          prisonerCount = prisoners.size,
          highComplexityOfNeedPrisonerCount = prisonersWithComplexNeeds.size,
          eligiblePrisonerCount = eligiblePrisoners.size,
          prisonersAssignedCount = activeAllocations,
          eligibleStaffCount = activeStaffCount,
          recordedSessionCount = cnSummaries.sessionCount,
          recordedEntryCount = cnSummaries.entryCount,
          receptionToAllocationDays = summaries.averageDaysToAllocation,
          receptionToRecordedEventDays = summaries.averageDaysToRecordedEntry,
        ),
      )
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
  getRecordedEntryDate: (String, LocalDate?) -> LocalDate?,
) {
  val data =
    personIdentifiers.map {
      val receptionDate = getReceptionDate(it)
      PersonSummary(
        it,
        receptionDate,
        getAllocationDate(it),
        getRecordedEntryDate(it, receptionDate),
      )
    }

  private fun PersonSummary.eligibilityDateIsValid() = eligibilityDate?.isAfter(LocalDate.now().minusMonths(6)) ?: false

  private val allocationDays =
    data.mapNotNull { if (it.eligibilityDateIsValid()) it.eligibilityToAllocationInDays else null }
  private val recordedEntryDays =
    data.mapNotNull { if (it.eligibilityDateIsValid()) it.eligibilityToRecordedEntryInDays else null }

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
