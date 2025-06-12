package uk.gov.justice.digital.hmpps.keyworker.services

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.keyworker.domain.PrisonConfiguration
import uk.gov.justice.digital.hmpps.keyworker.domain.PrisonConfigurationRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.StaffAllocationRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.StaffConfigRepository
import uk.gov.justice.digital.hmpps.keyworker.dto.NoRecommendation
import uk.gov.justice.digital.hmpps.keyworker.dto.PersonSearchRequest
import uk.gov.justice.digital.hmpps.keyworker.dto.PrisonerSummary
import uk.gov.justice.digital.hmpps.keyworker.dto.RecommendedAllocation
import uk.gov.justice.digital.hmpps.keyworker.dto.RecommendedAllocations
import uk.gov.justice.digital.hmpps.keyworker.sar.StaffSummary
import java.time.LocalDateTime
import java.util.SortedSet
import java.util.TreeSet

@Service
class AllocationRecommender(
  private val prisonConfigRepository: PrisonConfigurationRepository,
  private val personSearch: PersonSearch,
  private val staffSearch: StaffSearch,
  private val staffConfigRepository: StaffConfigRepository,
  private val staffAllocationRepository: StaffAllocationRepository,
) {
  fun allocations(prisonCode: String): RecommendedAllocations {
    val people =
      personSearch
        .findPeople(prisonCode, PersonSearchRequest(excludeActiveAllocations = true))
        .content
        .filter { !it.hasHighComplexityOfNeeds }
        .sortedWith(compareBy(PrisonerSummary::lastName, PrisonerSummary::firstName, PrisonerSummary::personIdentifier))
    val staff = getStaffCapacities(prisonCode)
    val staffIds = staff.map { it.staff.staffId }.toSet()

    val recommendations =
      people.map { person ->
        val previousAllocations =
          staffAllocationRepository.findPreviousAllocations(prisonCode, person.personIdentifier, staffIds)
        val recommended =
          if (previousAllocations.isNotEmpty()) {
            staff.first { it.staff.staffId in previousAllocations }
          } else {
            staff.firstOrNull { it.allocationCount < it.autoAllocationCapacity }
          }?.also {
            staff.remove(it)
            it.allocationCount++
            staff.add(it)
          }

        recommended?.let { RecommendedAllocation(person.personIdentifier, it.staff) }
          ?: NoRecommendation(person.personIdentifier)
      }

    return RecommendedAllocations(
      recommendations.filterIsInstance<RecommendedAllocation>(),
      recommendations.filterIsInstance<NoRecommendation>().map { it.personIdentifier },
      staff.map { it.staff },
    )
  }

  private fun getStaffCapacities(prisonCode: String): SortedSet<StaffCapacity> {
    val prisonConfig = prisonConfigRepository.findByCode(prisonCode) ?: PrisonConfiguration.default(prisonCode)
    val staff = staffSearch.findStaff(prisonCode)
    val staffIds = staff.map { it.staffId }.toSet()
    val staffInfo = staffConfigRepository.findAllWithAllocationCount(prisonCode, staffIds).associateBy { it.staffId }
    val autoAllocations = staffAllocationRepository.findLatestAutoAllocationsFor(staffIds).associateBy { it.staffId }
    return staff
      .map {
        val staffInfo = staffInfo[it.staffId]
        StaffCapacity(
          StaffSummary(it.staffId, it.firstName, it.lastName),
          staffInfo?.staffConfig?.capacity ?: prisonConfig.maximumCapacity,
          staffInfo?.allocationCount ?: 0,
          autoAllocations[it.staffId]?.allocatedAt,
        )
      }.sortedForAllocation()
  }
}

private class StaffCapacity(
  val staff: StaffSummary,
  val autoAllocationCapacity: Int,
  var allocationCount: Int,
  val lastAutoAllocationAt: LocalDateTime?,
) {
  fun availability(): Double =
    if (allocationCount == 0 || autoAllocationCapacity == 0) 0.0 else allocationCount / autoAllocationCapacity.toDouble()
}

private fun List<StaffCapacity>.sortedForAllocation(): TreeSet<StaffCapacity> =
  TreeSet(
    compareBy(StaffCapacity::availability)
      .thenByDescending(StaffCapacity::lastAutoAllocationAt)
      .thenBy { it.staff.staffId },
  ).apply { addAll(this@sortedForAllocation) }
