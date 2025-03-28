package uk.gov.justice.digital.hmpps.keyworker.services

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.keyworker.domain.KeyworkerAllocationRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceData
import uk.gov.justice.digital.hmpps.keyworker.dto.Actioned
import uk.gov.justice.digital.hmpps.keyworker.dto.CodedDescription
import uk.gov.justice.digital.hmpps.keyworker.dto.Keyworker
import uk.gov.justice.digital.hmpps.keyworker.dto.KeyworkerAllocation
import uk.gov.justice.digital.hmpps.keyworker.dto.PersonStaffAllocationHistory
import uk.gov.justice.digital.hmpps.keyworker.integration.ManageUsersClient
import uk.gov.justice.digital.hmpps.keyworker.integration.UserDetails
import uk.gov.justice.digital.hmpps.keyworker.sar.StaffSummary
import uk.gov.justice.digital.hmpps.keyworker.sar.internal.StaffDetailProvider

@Service
class GetKeyworkerAllocations(
  private val allocationRepository: KeyworkerAllocationRepository,
  private val manageUsers: ManageUsersClient,
  private val staffDetailProvider: StaffDetailProvider,
  private val prisonService: PrisonService,
) {
  fun allocationHistoryFor(prisonNumber: String): PersonStaffAllocationHistory {
    val allocations = allocationRepository.findAllByPersonIdentifier(prisonNumber)
    val usernames = allocations.flatMap { listOfNotNull(it.allocatedBy, it.lastModifiedBy) }.toSet()
    val users = manageUsers.getUsersDetails(usernames).associateBy { it.username }
    check(users.keys.containsAll(usernames))
    val prisons = prisonService.findPrisons(allocations.map { it.prisonCode }.toSet()).associateBy { it.prisonId }
    check(prisons.keys.containsAll(allocations.map { it.prisonCode }.toSet()))
    val staffIds = allocations.map { it.staffId }.toSet()
    val staff = staffDetailProvider.findStaffSummariesFromIds(staffIds).associateBy { it.staffId }
    check(staff.keys.containsAll(staffIds))

    return PersonStaffAllocationHistory(
      prisonNumber,
      allocations
        .map {
          it.toModel(
            { prisons[it]!!.asCodedDescription() },
            { staff[it]!!.asKeyworker() },
            { users[it]!! },
          )
        }.sortedByDescending { it.allocated.at },
    )
  }

  private fun uk.gov.justice.digital.hmpps.keyworker.domain.KeyworkerAllocation.toModel(
    prison: (String) -> CodedDescription,
    keyworker: (Long) -> Keyworker,
    actionedBy: (String) -> UserDetails,
  ): KeyworkerAllocation =
    KeyworkerAllocation(
      active,
      keyworker(staffId),
      prison(prisonCode),
      Actioned(assignedAt, actionedBy(allocatedBy).name, allocationReason.asCodedDescription()),
      deallocationReason?.let {
        Actioned(
          expiryDateTime!!,
          actionedBy(lastModifiedBy!!).name,
          it.asCodedDescription(),
        )
      },
    )

  private fun StaffSummary.asKeyworker() = Keyworker(staffId, firstName, lastName)

  private fun Prison.asCodedDescription() = CodedDescription(prisonId, prisonName)

  private fun ReferenceData.asCodedDescription() = CodedDescription(code, description)
}
