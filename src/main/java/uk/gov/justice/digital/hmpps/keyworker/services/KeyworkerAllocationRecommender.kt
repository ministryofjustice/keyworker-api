package uk.gov.justice.digital.hmpps.keyworker.services

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.keyworker.domain.KeyworkerAllocationRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.KeyworkerConfigRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.PrisonConfiguration
import uk.gov.justice.digital.hmpps.keyworker.domain.PrisonConfigurationRepository
import uk.gov.justice.digital.hmpps.keyworker.dto.Keyworker
import uk.gov.justice.digital.hmpps.keyworker.dto.NoRecommendation
import uk.gov.justice.digital.hmpps.keyworker.dto.PagingAndSortingDto.activeStaffKeyWorkersPagingAndSorting
import uk.gov.justice.digital.hmpps.keyworker.dto.PersonSearchRequest
import uk.gov.justice.digital.hmpps.keyworker.dto.PrisonerSummary
import uk.gov.justice.digital.hmpps.keyworker.dto.RecommendedAllocation
import uk.gov.justice.digital.hmpps.keyworker.dto.RecommendedAllocations
import java.time.LocalDateTime
import java.util.Optional
import java.util.SortedSet
import java.util.TreeSet

@Service
class KeyworkerAllocationRecommender(
  private val prisonConfigRepository: PrisonConfigurationRepository,
  private val personSearch: PersonSearch,
  private val nomisService: NomisService,
  private val keyworkerConfigRepository: KeyworkerConfigRepository,
  private val keyworkerAllocationRepository: KeyworkerAllocationRepository,
) {
  fun allocations(prisonCode: String): RecommendedAllocations {
    val people =
      personSearch
        .findPeople(prisonCode, PersonSearchRequest(excludeActiveAllocations = true))
        .content
        .filter { !it.hasHighComplexityOfNeeds }
        .sortedWith(compareBy(PrisonerSummary::lastName, PrisonerSummary::firstName, PrisonerSummary::personIdentifier))
    val keyworkers = getKeyworkerCapacities(prisonCode)
    val keyworkerIds = keyworkers.map { it.keyworker.staffId }.toSet()

    val recommendations =
      people.map { person ->
        val previousAllocations =
          keyworkerAllocationRepository.findPreviousKeyworkerAllocations(
            prisonCode,
            person.personIdentifier,
            keyworkerIds,
          )
        val recommended =
          if (previousAllocations.isNotEmpty()) {
            keyworkers.first { it.keyworker.staffId in previousAllocations }
          } else {
            keyworkers.firstOrNull { it.allocationCount < it.autoAllocationCapacity }
          }?.also {
            keyworkers.remove(it)
            it.allocationCount++
            keyworkers.add(it)
          }

        recommended?.let { RecommendedAllocation(person.personIdentifier, it.keyworker) }
          ?: NoRecommendation(person.personIdentifier)
      }

    return RecommendedAllocations(
      recommendations.filterIsInstance<RecommendedAllocation>(),
      recommendations.filterIsInstance<NoRecommendation>().map { it.personIdentifier },
    )
  }

  private fun getKeyworkerCapacities(prisonCode: String): SortedSet<KeyworkerCapacity> {
    val prisonConfig = prisonConfigRepository.findByCode(prisonCode) ?: PrisonConfiguration.default(prisonCode)
    return nomisService
      .getActiveStaffKeyWorkersForPrison(
        prisonCode,
        Optional.empty(),
        activeStaffKeyWorkersPagingAndSorting(),
        true,
      )?.body
      ?.let { nomisKeyworkers ->
        val keyworkerIds = nomisKeyworkers.map { it.staffId }.toSet()
        val keyworkerInfo =
          keyworkerConfigRepository.findAllWithAllocationCount(prisonCode, keyworkerIds).associateBy { it.staffId }
        val autoAllocations =
          keyworkerAllocationRepository.findLatestAutoAllocationsFor(keyworkerIds).associateBy { it.staffId }
        nomisKeyworkers.map {
          val keyworkerInfo = keyworkerInfo[it.staffId]
          val capacity = keyworkerInfo?.keyworkerConfig?.capacity ?: prisonConfig.capacity
          val autoAllocationCapacity = capacity * prisonConfig.maximumCapacity / prisonConfig.capacity

          KeyworkerCapacity(
            Keyworker(it.staffId, it.firstName, it.lastName),
            autoAllocationCapacity,
            keyworkerInfo?.allocationCount ?: 0,
            autoAllocations[it.staffId]?.assignedAt,
          )
        }
      }?.sortedForAllocation() ?: sortedSetOf()
  }
}

private class KeyworkerCapacity(
  val keyworker: Keyworker,
  val autoAllocationCapacity: Int,
  var allocationCount: Int,
  val lastAutoAllocationAt: LocalDateTime?,
) {
  fun availability(): Double =
    if (allocationCount == 0 || autoAllocationCapacity == 0) 0.0 else allocationCount / autoAllocationCapacity.toDouble()
}

private fun List<KeyworkerCapacity>.sortedForAllocation(): TreeSet<KeyworkerCapacity> =
  TreeSet(
    compareBy(KeyworkerCapacity::availability)
      .thenByDescending(KeyworkerCapacity::lastAutoAllocationAt)
      .thenBy { it.keyworker.staffId },
  ).apply { addAll(this@sortedForAllocation) }
