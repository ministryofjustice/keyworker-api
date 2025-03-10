package uk.gov.justice.digital.hmpps.keyworker.services

import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.keyworker.dto.Allocation
import uk.gov.justice.digital.hmpps.keyworker.dto.CodedDescription
import uk.gov.justice.digital.hmpps.keyworker.dto.Keyworker
import uk.gov.justice.digital.hmpps.keyworker.dto.KeyworkerDetails
import uk.gov.justice.digital.hmpps.keyworker.dto.KeyworkerSessionStats
import uk.gov.justice.digital.hmpps.keyworker.dto.KeyworkerStats
import uk.gov.justice.digital.hmpps.keyworker.dto.LatestKeyworkerSession
import uk.gov.justice.digital.hmpps.keyworker.dto.ScheduleType
import uk.gov.justice.digital.hmpps.keyworker.dto.StaffLocationRoleDto
import uk.gov.justice.digital.hmpps.keyworker.integration.Prisoner
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNoteSummary
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNotesApiClient
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.UsageByPersonIdentifierRequest.Companion.keyworkerTypes
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.summary
import uk.gov.justice.digital.hmpps.keyworker.integration.prisonersearch.PrisonerSearchClient
import uk.gov.justice.digital.hmpps.keyworker.model.KeyworkerStatus
import uk.gov.justice.digital.hmpps.keyworker.statistics.internal.KeyworkerAllocation
import uk.gov.justice.digital.hmpps.keyworker.statistics.internal.KeyworkerAllocationRepository
import uk.gov.justice.digital.hmpps.keyworker.statistics.internal.KeyworkerRepository
import uk.gov.justice.digital.hmpps.keyworker.statistics.internal.PrisonConfig
import uk.gov.justice.digital.hmpps.keyworker.statistics.internal.PrisonConfigRepository
import java.time.LocalDate
import java.time.LocalDate.now
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit.DAYS
import java.time.temporal.ChronoUnit.WEEKS
import kotlin.math.roundToInt
import uk.gov.justice.digital.hmpps.keyworker.dto.Prisoner as Person

@Service
class GetKeyworkerDetails(
  private val prisonConfigRepository: PrisonConfigRepository,
  private val nomisService: NomisService,
  private val keyworkerRepository: KeyworkerRepository,
  private val allocationRepository: KeyworkerAllocationRepository,
  private val prisonerSearch: PrisonerSearchClient,
  private val caseNotesApiClient: CaseNotesApiClient,
) {
  fun getFor(
    prisonCode: String,
    staffId: Long,
  ): KeyworkerDetails {
    val prisonConfig = prisonConfigRepository.findByIdOrNull(prisonCode) ?: PrisonConfig.default(prisonCode)
    val keyworker =
      nomisService
        .getStaffKeyWorkerForPrison(prisonCode, staffId)
        .orElseThrow { IllegalArgumentException("Staff not recognised as a keyworker") }
        .asKeyworker()

    val keyworkerInfo = keyworkerRepository.findAllWithAllocationCount(setOf(staffId)).firstOrNull()
    val allocations = allocationRepository.findActiveForPrisonStaff(prisonCode, staffId)
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

    val (current, cnSummary) = allocations.keyworkerSessionStats(now().minusMonths(1), now(), prisonConfig, staffId)
    val (previous, _) =
      allocations.keyworkerSessionStats(now().minusMonths(2), now().minusMonths(1), prisonConfig, staffId)

    return KeyworkerDetails(
      keyworker,
      (keyworkerInfo?.keyworker?.status ?: KeyworkerStatus.ACTIVE).codedDescription(),
      CodedDescription(prisonCode, prisonName),
      keyworkerInfo?.keyworker?.capacity ?: prisonConfig.capacityTier1,
      keyworkerInfo?.allocationCount ?: 0,
      allocations
        .mapNotNull { alloc ->
          prisonerDetails[alloc.personIdentifier]?.let {
            alloc.asAllocation(it, cnSummary?.findSessionDate(it.prisonerNumber))
          }
        }.sortedWith(compareBy({ it.prisoner.lastName }, { it.prisoner.firstName })),
      KeyworkerStats(current, previous),
    )
  }

  private fun List<KeyworkerAllocation>.keyworkerSessionStats(
    from: LocalDate,
    to: LocalDate,
    prisonConfig: PrisonConfig,
    staffId: Long,
  ): Pair<KeyworkerSessionStats, CaseNoteSummary?> {
    val applicableAllocations =
      filter {
        it.expiryDateTime == null || !it.expiryDateTime!!.isBefore(from.atStartOfDay())
      }
    val personIdentifiers = applicableAllocations.map { it.personIdentifier }.toSet()
    val cnSummary =
      if (personIdentifiers.isEmpty()) {
        null
      } else {
        caseNotesApiClient
          .getUsageByPersonIdentifier(
            keyworkerTypes(personIdentifiers, from, to, setOf(staffId.toString())),
          ).summary()
      }
    val total = applicableAllocations.sumOf { it.daysAllocatedForStats(from, to) }
    val averagePerDay = if (total == 0L) null else total / DAYS.between(from, to).toDouble()
    val projectedSessions =
      if (averagePerDay == null) {
        null
      } else {
        val sessionMultiplier = Math.floorDiv(WEEKS.between(from, to), prisonConfig.kwSessionFrequencyInWeeks)
        (averagePerDay * sessionMultiplier).roundToInt()
      }
    val compliance =
      if (projectedSessions == null || cnSummary == null) {
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
        projectedSessions ?: 0,
        cnSummary?.totalSessions ?: 0,
        cnSummary?.totalEntries ?: 0,
        compliance ?: 0.0,
      ),
      cnSummary,
    )
  }
}

private fun StaffLocationRoleDto.asKeyworker() =
  Keyworker(staffId, firstName, lastName, ScheduleType.from(scheduleType).toCodedDescription())

private fun ScheduleType.toCodedDescription() = CodedDescription(code, description)

private fun Prisoner.asPrisoner() = Person(prisonerNumber, firstName, lastName, csra)

private fun KeyworkerAllocation.asAllocation(
  prisoner: Prisoner,
  latestSession: LocalDate?,
) = Allocation(
  prisoner.asPrisoner(),
  prisoner.prisonName,
  prisoner.releaseDate,
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
