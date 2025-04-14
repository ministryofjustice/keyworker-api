package uk.gov.justice.digital.hmpps.keyworker.services

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.keyworker.domain.KeyworkerAllocationRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceData
import uk.gov.justice.digital.hmpps.keyworker.dto.Actioned
import uk.gov.justice.digital.hmpps.keyworker.dto.CodedDescription
import uk.gov.justice.digital.hmpps.keyworker.dto.CurrentAllocation
import uk.gov.justice.digital.hmpps.keyworker.dto.CurrentKeyworker
import uk.gov.justice.digital.hmpps.keyworker.dto.CurrentPersonStaffAllocation
import uk.gov.justice.digital.hmpps.keyworker.dto.Keyworker
import uk.gov.justice.digital.hmpps.keyworker.dto.KeyworkerAllocation
import uk.gov.justice.digital.hmpps.keyworker.dto.PersonStaffAllocationHistory
import uk.gov.justice.digital.hmpps.keyworker.events.ComplexityOfNeedLevel
import uk.gov.justice.digital.hmpps.keyworker.integration.ManageUsersClient
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNotesApiClient
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.UsageByPersonIdentifierRequest.Companion.sessionTypes
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.summary
import uk.gov.justice.digital.hmpps.keyworker.sar.StaffSummary
import uk.gov.justice.digital.hmpps.keyworker.sar.internal.StaffDetailProvider
import java.time.LocalDate.now

@Service
class GetKeyworkerAllocations(
  private val complexityOfNeed: ComplexityOfNeedGateway,
  private val allocationRepository: KeyworkerAllocationRepository,
  private val manageUsers: ManageUsersClient,
  private val staffDetailProvider: StaffDetailProvider,
  private val prisonService: PrisonService,
  private val caseNotesApiClient: CaseNotesApiClient,
) {
  fun currentFor(prisonNumber: String): CurrentPersonStaffAllocation {
    val level = complexityOfNeed.getOffendersWithMeasuredComplexityOfNeed(setOf(prisonNumber)).firstOrNull()?.level
    return when (level) {
      ComplexityOfNeedLevel.HIGH -> CurrentPersonStaffAllocation(prisonNumber, true, null, null)
      else ->
        allocationRepository
          .findFirstByPersonIdentifierAndActiveIsTrueOrderByAssignedAtDesc(prisonNumber)
          ?.let { allocation ->
            val cns =
              caseNotesApiClient.getUsageByPersonIdentifier(
                sessionTypes(
                  allocation.prisonCode,
                  setOf(prisonNumber),
                  from = now().minusMonths(38),
                  to = now(),
                  staffIds = setOf("${allocation.staffId}"),
                ),
              )
            staffDetailProvider.findStaffSummariesFromIds(setOf(allocation.staffId)).firstOrNull()?.let {
              CurrentPersonStaffAllocation(
                prisonNumber,
                false,
                CurrentAllocation(it.asCurrentKeyworker(), allocation.prisonCode),
                cns.summary().findSessionDate(prisonNumber),
              )
            }
          } ?: CurrentPersonStaffAllocation(prisonNumber, false, null, null)
    }
  }

  fun historyFor(prisonNumber: String): PersonStaffAllocationHistory {
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
            { username -> username?.let { users[it]?.name } ?: "User" },
          )
        }.sortedByDescending { it.allocated.at },
    )
  }

  private fun uk.gov.justice.digital.hmpps.keyworker.domain.KeyworkerAllocation.toModel(
    prison: (String) -> CodedDescription,
    keyworker: (Long) -> Keyworker,
    actionedBy: (String?) -> String,
  ): KeyworkerAllocation =
    KeyworkerAllocation(
      active,
      keyworker(staffId),
      prison(prisonCode),
      Actioned(assignedAt, actionedBy(allocatedBy), allocationReason.asCodedDescription()),
      deallocationReason?.let {
        Actioned(
          expiryDateTime!!,
          actionedBy(lastModifiedBy),
          it.asCodedDescription(),
        )
      },
    )

  private fun StaffSummary.asKeyworker() = Keyworker(staffId, firstName, lastName)

  private fun Prison.asCodedDescription() = CodedDescription(prisonId, prisonName)

  private fun ReferenceData.asCodedDescription() = CodedDescription(code, description)

  private fun StaffSummary.asCurrentKeyworker() = CurrentKeyworker(firstName, lastName)
}
