package uk.gov.justice.digital.hmpps.keyworker.statistics

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.keyworker.dto.PagingAndSortingDto.activeStaffKeyWorkersPagingAndSorting
import uk.gov.justice.digital.hmpps.keyworker.events.ComplexityOfNeedLevel.HIGH
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNotesApiClient
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.UsageByPersonIdentifierRequest.Companion.keyworkerTypes
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.UsageByPersonIdentifierRequest.Companion.sessionTypes
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.UsageByPersonIdentifierRequest.Companion.transferTypes
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.summary
import uk.gov.justice.digital.hmpps.keyworker.integration.events.HmppsDomainEvent
import uk.gov.justice.digital.hmpps.keyworker.integration.events.PrisonStatisticsInfo
import uk.gov.justice.digital.hmpps.keyworker.integration.prisonersearch.PrisonerSearchClient
import uk.gov.justice.digital.hmpps.keyworker.model.KeyworkerStatus
import uk.gov.justice.digital.hmpps.keyworker.services.ComplexityOfNeedGateway
import uk.gov.justice.digital.hmpps.keyworker.services.NomisService
import uk.gov.justice.digital.hmpps.keyworker.statistics.internal.KeyworkerAllocationRepository
import uk.gov.justice.digital.hmpps.keyworker.statistics.internal.KeyworkerRepository
import uk.gov.justice.digital.hmpps.keyworker.statistics.internal.PrisonConfigRepository
import uk.gov.justice.digital.hmpps.keyworker.statistics.internal.PrisonStatistic
import uk.gov.justice.digital.hmpps.keyworker.statistics.internal.PrisonStatisticRepository
import java.time.LocalDate
import java.time.format.DateTimeFormatter.ISO_LOCAL_DATE
import java.time.temporal.ChronoUnit.DAYS
import java.util.Optional

@Service
class PrisonStatisticCalculator(
  private val statisticRepository: PrisonStatisticRepository,
  private val prisonConfigRepository: PrisonConfigRepository,
  private val prisonerSearch: PrisonerSearchClient,
  private val complexityOfNeed: ComplexityOfNeedGateway,
  private val keyworkerAllocationRepository: KeyworkerAllocationRepository,
  private val caseNotesApi: CaseNotesApiClient,
  private val nomisService: NomisService,
  private val keyworkerRepository: KeyworkerRepository,
  private val telemetryClient: TelemetryClient,
) {
  fun calculate(info: HmppsDomainEvent<PrisonStatisticsInfo>) {
    with(info.additionalInformation) {
      val stats = statisticRepository.findByPrisonCodeAndDate(prisonCode, date)

      if (stats != null) return

      val prisonConfig = prisonConfigRepository.findByIdOrNull(prisonCode)
      val prisoners = prisonerSearch.findAllPrisoners(prisonCode)

      if (prisoners.isEmpty()) {
        telemetryClient.trackEvent(
          "EmptyPrison",
          mapOf("prisonCode" to prisonCode, "date" to ISO_LOCAL_DATE.format(date)),
          null,
        )
        return
      }

      val prisonersWithComplexNeeds =
        if (prisonConfig?.hasPrisonersWithHighComplexityNeeds == true) {
          complexityOfNeed
            .getOffendersWithMeasuredComplexityOfNeed(prisoners.personIdentifiers())
            .filter { it.level == HIGH }
        } else {
          emptyList()
        }
      val eligiblePrisoners = (prisoners.personIdentifiers() - prisonersWithComplexNeeds.map { it.offenderNo }).toSet()

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

      val activeAllocations = keyworkerAllocationRepository.countActiveAllocations(prisonCode, eligiblePrisoners)
      val newAllocations =
        keyworkerAllocationRepository
          .findNewAllocationsAt(prisonCode, date, date.plusDays(1))
          .associateBy { it.personIdentifier }

      val cnSummary = caseNotesApi.getUsageByPersonIdentifier(keyworkerTypes(eligiblePrisoners, date)).summary()
      val peopleWithSessions = cnSummary.personIdentifiersWithSessions()
      val previousSessions =
        if (peopleWithSessions.isNotEmpty()) {
          caseNotesApi
            .getUsageByPersonIdentifier(
              sessionTypes(peopleWithSessions, date.minusMonths(6), date.minusDays(1)),
            ).summary()
        } else {
          null
        }

      val transferSummary =
        caseNotesApi
          .getUsageByPersonIdentifier(
            transferTypes(eligiblePrisoners, date.minusMonths(6), date.minusDays(1)),
          ).summary()

      val activeKeyworkers =
        nomisService
          .getActiveStaffKeyWorkersForPrison(
            prisonCode,
            Optional.empty(),
            activeStaffKeyWorkersPagingAndSorting(),
            true,
          )?.body
          ?.let { nomisKeyworkers ->
            val dpsKeyworkers =
              keyworkerRepository
                .findAllByStaffIdIn(nomisKeyworkers.map { it.staffId }.toSet())
                .associate { it.staffId to it.status }
                .toMutableMap()
            nomisKeyworkers.forEach { dpsKeyworkers.putIfAbsent(it.staffId, KeyworkerStatus.ACTIVE) }
            dpsKeyworkers.count { it.value == KeyworkerStatus.ACTIVE }
          } ?: 0

      val summaries =
        PeopleSummaries(
          eligiblePrisoners,
          {
            val transferDate = transferSummary.findTransferDate(it)
            val receptionDate = prisoners.findByPersonIdentifier(it)?.receptionDate
            if (transferDate != receptionDate) {
              telemetryClient.trackEvent(
                "ReceptionDateNotMatched",
                listOfNotNull(
                  "personIdentifier" to it,
                  "prisonCode" to prisonCode,
                  receptionDate?.let { "receptionDate" to ISO_LOCAL_DATE.format(it) },
                  transferDate?.let { "transferDate" to ISO_LOCAL_DATE.format(it) },
                ).toMap(),
                mapOf(),
              )
            }
            receptionDate
          },
          { newAllocations[it]?.assignedAt?.toLocalDate() },
          { pi -> cnSummary.findSessionDate(pi)?.takeIf { previousSessions?.findSessionDate(pi) == null } },
        )

      statisticRepository.save(
        PrisonStatistic(
          prisonCode = prisonCode,
          date = date,
          totalPrisoners = prisoners.size,
          eligiblePrisoners = eligiblePrisoners.size,
          assignedKeyworker = activeAllocations,
          activeKeyworkers = activeKeyworkers,
          keyworkerSessions = cnSummary.totalSessions,
          keyworkerEntries = cnSummary.totalEntries,
          averageReceptionToAllocationDays = summaries.averageDaysToAllocation,
          averageReceptionToSessionDays = summaries.averageDaysToSession,
        ),
      )
    }
  }
}

class PeopleSummaries(
  personIdentifiers: Set<String>,
  getReceptionDate: (String) -> LocalDate?,
  getAllocationDate: (String) -> LocalDate?,
  getSessionDate: (String) -> LocalDate?,
) {
  val data =
    personIdentifiers.map {
      PersonSummary(
        it,
        getReceptionDate(it).takeIf { LocalDate.now().minusMonths(6).isBefore(it) },
        getAllocationDate(it),
        getSessionDate(it),
      )
    }

  private val allocationDays = data.mapNotNull { it.receptionToAllocationInDays }
  private val sessionDays = data.mapNotNull { it.receptionToSessionInDays }

  val averageDaysToAllocation = if (allocationDays.isEmpty()) null else allocationDays.average().toInt()
  val averageDaysToSession = if (sessionDays.isEmpty()) null else sessionDays.average().toInt()
}

data class PersonSummary(
  val personIdentifier: String,
  val receptionDate: LocalDate?,
  val allocationDate: LocalDate?,
  val sessionDate: LocalDate?,
) {
  val receptionToAllocationInDays =
    if (receptionDate != null && allocationDate != null && allocationDate >= receptionDate) {
      DAYS.between(receptionDate, allocationDate).toInt()
    } else {
      null
    }
  val receptionToSessionInDays =
    if (receptionDate != null && sessionDate != null && sessionDate >= receptionDate) {
      DAYS.between(receptionDate, sessionDate).toInt()
    } else {
      null
    }
}
