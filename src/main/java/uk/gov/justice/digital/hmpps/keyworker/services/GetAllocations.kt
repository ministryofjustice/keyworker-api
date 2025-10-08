package uk.gov.justice.digital.hmpps.keyworker.services

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.keyworker.domain.Allocation
import uk.gov.justice.digital.hmpps.keyworker.domain.AllocationRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.asCodedDescription
import uk.gov.justice.digital.hmpps.keyworker.dto.CodedDescription
import uk.gov.justice.digital.hmpps.keyworker.dto.person.Actioned
import uk.gov.justice.digital.hmpps.keyworker.dto.person.StaffAllocation
import uk.gov.justice.digital.hmpps.keyworker.dto.person.StaffAllocationHistory
import uk.gov.justice.digital.hmpps.keyworker.dto.staff.StaffSummary
import uk.gov.justice.digital.hmpps.keyworker.integration.ManageUsersClient
import uk.gov.justice.digital.hmpps.keyworker.integration.prisonapi.PrisonApiClient

@Service
class GetAllocations(
  private val allocationRepository: AllocationRepository,
  private val manageUsers: ManageUsersClient,
  private val prisonApi: PrisonApiClient,
  private val prisonService: PrisonService,
) {
  fun historyFor(prisonNumber: String): StaffAllocationHistory {
    val allocations = allocationRepository.findAllByPersonIdentifier(prisonNumber)
    val usernames = allocations.flatMap { listOfNotNull(it.allocatedBy, it.deallocatedBy) }.toSet()
    val users = manageUsers.getUsersDetails(usernames).associateBy { it.username }
    check(users.keys.containsAll(usernames))
    val prisons = prisonService.findPrisons(allocations.map { it.prisonCode }.toSet()).associateBy { it.prisonId }
    check(prisons.keys.containsAll(allocations.map { it.prisonCode }.toSet()))
    val staffIds = allocations.map { it.staffId }.toSet()
    val staff = prisonApi.getStaffSummariesFromIds(staffIds).associateBy { it.staffId }
    check(staff.keys.containsAll(staffIds))

    return StaffAllocationHistory(
      prisonNumber,
      allocations
        .map { allocation ->
          allocation.toModel(
            { prisons[it]!!.asCodedDescription() },
            { staff[it]!! },
            { username -> username?.let { users[it]?.name } ?: "User" },
          )
        }.sortedByDescending { it.allocated.at },
    )
  }

  private fun Allocation.toModel(
    prison: (String) -> CodedDescription,
    staff: (Long) -> StaffSummary,
    actionedBy: (String?) -> String,
  ): StaffAllocation =
    StaffAllocation(
      isActive,
      staff(staffId),
      prison(prisonCode),
      Actioned(allocatedAt, actionedBy(allocatedBy), allocationReason.asCodedDescription()),
      deallocationReason?.let {
        Actioned(
          deallocatedAt!!,
          actionedBy(deallocatedBy),
          it.asCodedDescription(),
        )
      },
    )
}
