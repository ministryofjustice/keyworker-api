package uk.gov.justice.digital.hmpps.keyworker.sar

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.keyworker.domain.Allocation
import uk.gov.justice.digital.hmpps.keyworker.domain.AllocationRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.Policy
import uk.gov.justice.digital.hmpps.keyworker.domain.PolicyRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.asCodedDescription
import uk.gov.justice.digital.hmpps.keyworker.integration.PrisonApiClient
import uk.gov.justice.digital.hmpps.keyworker.model.AllocationType
import java.time.LocalDate

@Service
class SubjectAccessRequest(
  private val allocationRepository: AllocationRepository,
  private val policyRepository: PolicyRepository,
  private val prisonApi: PrisonApiClient,
) {
  fun getSarContent(
    prn: String,
    fromDate: LocalDate?,
    toDate: LocalDate?,
  ): SubjectAccessResponse? {
    val allocations = allocationRepository.findAllocationsForSar(prn, fromDate, toDate)
    val policyMap = policyRepository.findAll().associateBy(Policy::code)
    val staffMap: Map<Long, StaffMember> =
      prisonApi.findStaffSummariesFromIds(allocations.map { it.staffId }.toSet()).associate {
        it.staffId to StaffMember(it.firstName, it.lastName)
      }
    val result =
      allocations
        .asSequence()
        .map { a -> a.forSarReport({ requireNotNull(staffMap[it]) }, { requireNotNull(policyMap[it]) }) }
        .sortedByDescending { it.allocatedAt }
        .toList()
    return if (result.isEmpty()) null else SubjectAccessResponse(prn, result)
  }

  private fun Allocation.forSarReport(
    getStaff: (Long) -> StaffMember,
    getPolicy: (String) -> Policy,
  ) = SarAllocation(
    allocatedAt,
    deallocatedAt,
    prisonCode,
    when (allocationType) {
      AllocationType.AUTO -> "Automatic"
      AllocationType.MANUAL -> "Manual"
      AllocationType.PROVISIONAL -> "Provisional"
    },
    allocationReason.description,
    deallocationReason?.description,
    getStaff(staffId),
    getStaff(staffId),
    getPolicy(policy).asCodedDescription(),
  )
}
