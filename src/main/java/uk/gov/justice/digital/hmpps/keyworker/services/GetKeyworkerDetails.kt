package uk.gov.justice.digital.hmpps.keyworker.services

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.keyworker.domain.KeyworkerAllocation
import uk.gov.justice.digital.hmpps.keyworker.domain.KeyworkerAllocationRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.PrisonConfiguration
import uk.gov.justice.digital.hmpps.keyworker.domain.PrisonConfigurationRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceDataDomain
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceDataRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.StaffConfigRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.asCodedDescription
import uk.gov.justice.digital.hmpps.keyworker.domain.getReferenceData
import uk.gov.justice.digital.hmpps.keyworker.domain.of
import uk.gov.justice.digital.hmpps.keyworker.domain.toKeyworkerStatusCodedDescription
import uk.gov.justice.digital.hmpps.keyworker.dto.Allocation
import uk.gov.justice.digital.hmpps.keyworker.dto.CodedDescription
import uk.gov.justice.digital.hmpps.keyworker.dto.KeyworkerDetails
import uk.gov.justice.digital.hmpps.keyworker.dto.KeyworkerSessionStats
import uk.gov.justice.digital.hmpps.keyworker.dto.KeyworkerStats
import uk.gov.justice.digital.hmpps.keyworker.dto.KeyworkerWithSchedule
import uk.gov.justice.digital.hmpps.keyworker.dto.LatestKeyworkerSession
import uk.gov.justice.digital.hmpps.keyworker.dto.StaffLocationRoleDto
import uk.gov.justice.digital.hmpps.keyworker.integration.Prisoner
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNoteSummary
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNotesApiClient
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.UsageByPersonIdentifierRequest.Companion.keyworkerTypes
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.summary
import uk.gov.justice.digital.hmpps.keyworker.integration.prisonersearch.PrisonerSearchClient
import java.time.LocalDate
import java.time.LocalDate.now
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit.DAYS
import uk.gov.justice.digital.hmpps.keyworker.dto.Prisoner as Person

@Service
class GetKeyworkerDetails(
  private val prisonConfigRepository: PrisonConfigurationRepository,
  private val nomisService: NomisService,
  private val staffConfigRepository: StaffConfigRepository,
  private val allocationRepository: KeyworkerAllocationRepository,
  private val prisonerSearch: PrisonerSearchClient,
  private val caseNotesApiClient: CaseNotesApiClient,
  private val referenceDataRepository: ReferenceDataRepository,
) {
  fun getFor(
    prisonCode: String,
    staffId: Long,
  ): KeyworkerDetails {
    val prisonConfig = prisonConfigRepository.findByCode(prisonCode) ?: PrisonConfiguration.default(prisonCode)
    val keyworker =
      nomisService
        .getStaffKeyWorkerForPrison(prisonCode, staffId)
        .orElseThrow { IllegalArgumentException("Staff not recognised as a keyworker") }
        .asKeyworker()

    val fromDate = now().minusMonths(1)
    val previousFromDate = fromDate.minusMonths(1)
    val keyworkerInfo = staffConfigRepository.findAllWithAllocationCount(prisonCode, setOf(staffId)).firstOrNull()
    val allocations =
      allocationRepository.findActiveForPrisonStaffBetween(
        prisonCode,
        staffId,
        previousFromDate.atStartOfDay(),
        now().atStartOfDay(),
      )
    val prisonerDetails =
      if (allocations.isEmpty()) {
        emptyMap()
      } else {
        prisonerSearch
          .findPrisonerDetails(allocations.map { it.personIdentifier }.toSet())
          .filter { it.prisonId == prisonCode }
          .associateBy { it.prisonerNumber }
      }
    val prisonName = prisonerDetails.values.firstOrNull()?.prisonName ?: nomisService.getAgency(prisonCode).description

    val (current, cnSummary) = allocations.keyworkerSessionStats(fromDate, now(), prisonConfig, staffId)
    val (previous, _) =
      allocations.keyworkerSessionStats(previousFromDate, fromDate, prisonConfig, staffId)

    return KeyworkerDetails(
      keyworker,
      keyworkerInfo?.staffConfig?.status.toKeyworkerStatusCodedDescription(),
      CodedDescription(prisonCode, prisonName),
      keyworkerInfo?.staffConfig?.capacity ?: prisonConfig.capacity,
      keyworkerInfo?.allocationCount ?: 0,
      allocations
        .filter { it.active }
        .mapNotNull { alloc ->
          prisonerDetails[alloc.personIdentifier]?.let {
            alloc.asAllocation(it, cnSummary?.findSessionDate(it.prisonerNumber))
          }
        }.sortedWith(compareBy({ it.prisoner.lastName }, { it.prisoner.firstName })),
      KeyworkerStats(current, previous),
      keyworkerInfo?.staffConfig?.allowAutoAllocation ?: prisonConfig.allowAutoAllocation,
      keyworkerInfo?.staffConfig?.reactivateOn,
    )
  }

  private fun StaffLocationRoleDto.asKeyworker() =
    KeyworkerWithSchedule(
      staffId,
      firstName,
      lastName,
      referenceDataRepository.getReferenceData(ReferenceDataDomain.SCHEDULE_TYPE of scheduleType).asCodedDescription(),
    )

  private fun List<KeyworkerAllocation>.keyworkerSessionStats(
    from: LocalDate,
    to: LocalDate,
    prisonConfig: PrisonConfiguration,
    staffId: Long,
  ): Pair<KeyworkerSessionStats, CaseNoteSummary?> {
    val applicableAllocations =
      filter {
        (it.expiryDateTime == null || !it.expiryDateTime!!.toLocalDate().isBefore(from)) &&
          it.assignedAt.toLocalDate().isBefore(to)
      }
    val personIdentifiers = applicableAllocations.map { it.personIdentifier }.toSet()
    val cnSummary =
      if (personIdentifiers.isEmpty()) {
        null
      } else {
        caseNotesApiClient
          .getUsageByPersonIdentifier(
            keyworkerTypes(prisonConfig.code, personIdentifiers, from, to, setOf(staffId.toString())),
          ).summary()
      }
    val total = applicableAllocations.sumOf { it.daysAllocatedForStats(from, to) }
    val averagePerDay = if (total == 0L) 0 else total / DAYS.between(from, to)
    val projectedSessions =
      if (averagePerDay == 0L) {
        0
      } else {
        val sessionMultiplier = (DAYS.between(from, to.plusDays(1)) / (prisonConfig.frequencyInWeeks * 7.0))
        (averagePerDay * sessionMultiplier).toInt()
      }
    val compliance =
      if (projectedSessions == 0 || cnSummary == null) {
        null
      } else {
        Statistic.percentage(
          cnSummary.totalSessions,
          projectedSessions,
        )
      }

    return Pair(
      KeyworkerSessionStats(
        from,
        to,
        projectedSessions,
        cnSummary?.totalSessions ?: 0,
        cnSummary?.totalEntries ?: 0,
        compliance ?: 0.0,
      ),
      cnSummary,
    )
  }
}

private fun Prisoner.asPrisoner() = Person(prisonerNumber, firstName, lastName, csra, cellLocation, releaseDate)

private fun KeyworkerAllocation.asAllocation(
  prisoner: Prisoner,
  latestSession: LocalDate?,
) = Allocation(
  prisoner.asPrisoner(),
  latestSession?.let { LatestKeyworkerSession(it) },
)

private fun KeyworkerAllocation.daysAllocatedForStats(
  from: LocalDate,
  to: LocalDate,
): Long {
  val endTime: LocalDateTime = expiryDateTime ?: to.atStartOfDay()
  val startTime: LocalDateTime = maxOf(assignedAt, from.atStartOfDay())
  return DAYS.between(startTime, endTime)
}
