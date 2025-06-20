package uk.gov.justice.digital.hmpps.keyworker.services

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.keyworker.domain.StaffAllocationRepository
import uk.gov.justice.digital.hmpps.keyworker.dto.CurrentAllocation
import uk.gov.justice.digital.hmpps.keyworker.dto.CurrentKeyworker
import uk.gov.justice.digital.hmpps.keyworker.dto.CurrentPersonStaffAllocation
import uk.gov.justice.digital.hmpps.keyworker.dto.StaffSummary
import uk.gov.justice.digital.hmpps.keyworker.events.ComplexityOfNeedLevel
import uk.gov.justice.digital.hmpps.keyworker.integration.PrisonApiClient
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNotesApiClient
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.UsageByPersonIdentifierRequest.Companion.sessionTypes
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.summary
import java.time.LocalDate.now

@Service
class GetKeyworkerAllocations(
  private val complexityOfNeed: ComplexityOfNeedGateway,
  private val allocationRepository: StaffAllocationRepository,
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

  private fun StaffSummary.asCurrentKeyworker() = CurrentKeyworker(firstName, lastName)
}
