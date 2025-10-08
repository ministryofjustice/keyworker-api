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
import uk.gov.justice.digital.hmpps.keyworker.dto.staff.RecordedEventType
import uk.gov.justice.digital.hmpps.keyworker.events.ComplexityOfNeedLevel.HIGH
import uk.gov.justice.digital.hmpps.keyworker.integration.complexityofneed.ComplexityOfNeedApiClient
import uk.gov.justice.digital.hmpps.keyworker.integration.events.HmppsDomainEvent
import uk.gov.justice.digital.hmpps.keyworker.integration.events.PrisonStatisticsInfo
import uk.gov.justice.digital.hmpps.keyworker.integration.prisonapi.PrisonApiClient
import uk.gov.justice.digital.hmpps.keyworker.integration.prisonersearch.PrisonerSearchClient
import uk.gov.justice.digital.hmpps.keyworker.services.recordedevents.RecordedEventRetriever
import java.time.LocalDate
import java.time.temporal.ChronoUnit.DAYS

@Service
class PrisonStatisticCalculator(
  private val contextHolder: AllocationContextHolder,
  private val statisticRepository: PrisonStatisticRepository,
  private val prisonerSearch: PrisonerSearchClient,
  private val complexityOfNeedApi: ComplexityOfNeedApiClient,
  private val allocationRepository: AllocationRepository,
  private val recordedEventRetriever: RecordedEventRetriever,
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

      val cnSummaries = recordedEventRetriever.findRecordedEventSummaries(prisonCode, date, date)
      val previousRecordedEvent =
        recordedEventRetriever.findMostRecentEventBefore(
          prisonCode,
          cnSummaries.personIdentifiers(),
          date,
          when (policy) {
            AllocationPolicy.KEY_WORKER -> RecordedEventType.SESSION
            AllocationPolicy.PERSONAL_OFFICER -> RecordedEventType.ENTRY
          },
        )

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
              .findLatestRecordedEvent(pi)
              ?.occurredAt
              ?.toLocalDate()
              ?.takeIf {
                val previous = previousRecordedEvent[pi]?.occurredAt?.toLocalDate()
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
