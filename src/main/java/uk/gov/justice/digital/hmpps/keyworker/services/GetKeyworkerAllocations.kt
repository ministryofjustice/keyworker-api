package uk.gov.justice.digital.hmpps.keyworker.services

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.keyworker.domain.Allocation
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceData
import uk.gov.justice.digital.hmpps.keyworker.domain.StaffAllocationRepository
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
import uk.gov.justice.digital.hmpps.keyworker.integration.PrisonApiClient
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNotesApiClient
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.UsageByPersonIdentifierRequest.Companion.sessionTypes
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.summary
import uk.gov.justice.digital.hmpps.keyworker.sar.StaffSummary
import java.time.LocalDate.now

@Service
class GetKeyworkerAllocations(
  private val complexityOfNeed: ComplexityOfNeedGateway,
  private val allocationRepository: StaffAllocationRepository,
  private val manageUsers: ManageUsersClient,
  private val prisonApi: PrisonApiClient,
  private val prisonService: PrisonService,
  private val caseNotesApiClient: CaseNotesApiClient,
) {
  fun currentFor(
    prisonCode: String,
    prisonNumber: String,
  ): CurrentPersonStaffAllocation {
    val config = prisonService.getPrisonKeyworkerConfig(prisonCode)
    val level =
      if (config.hasPrisonersWithHighComplexityNeeds) {
        complexityOfNeed
          .getOffendersWithMeasuredComplexityOfNeed(setOf(prisonNumber))
          .firstOrNull { it.offenderNo == prisonNumber }
          ?.level
      } else {
        null
      }
    return when (level) {
      ComplexityOfNeedLevel.HIGH -> CurrentPersonStaffAllocation(prisonNumber, true, null, null)
      else ->
        allocationRepository
          .findFirstByPersonIdentifierAndIsActiveIsTrueOrderByAllocatedAtDesc(prisonNumber)
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
            prisonApi
              .findStaffSummariesFromIds(setOf(allocation.staffId))
              .firstOrNull { it.staffId == allocation.staffId }
              ?.let {
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
    val usernames = allocations.flatMap { listOfNotNull(it.allocatedBy, it.deallocatedBy) }.toSet()
    val users = manageUsers.getUsersDetails(usernames).associateBy { it.username }
    check(users.keys.containsAll(usernames))
    val prisons = prisonService.findPrisons(allocations.map { it.prisonCode }.toSet()).associateBy { it.prisonId }
    check(prisons.keys.containsAll(allocations.map { it.prisonCode }.toSet()))
    val staffIds = allocations.map { it.staffId }.toSet()
    val staff = prisonApi.findStaffSummariesFromIds(staffIds).associateBy { it.staffId }
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

  private fun Allocation.toModel(
    prison: (String) -> CodedDescription,
    keyworker: (Long) -> Keyworker,
    actionedBy: (String?) -> String,
  ): KeyworkerAllocation =
    KeyworkerAllocation(
      isActive,
      keyworker(staffId),
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

  private fun StaffSummary.asKeyworker() = Keyworker(staffId, firstName, lastName)

  private fun Prison.asCodedDescription() = CodedDescription(prisonId, prisonName)

  private fun ReferenceData.asCodedDescription() = CodedDescription(code, description)

  private fun StaffSummary.asCurrentKeyworker() = CurrentKeyworker(firstName, lastName)
}
