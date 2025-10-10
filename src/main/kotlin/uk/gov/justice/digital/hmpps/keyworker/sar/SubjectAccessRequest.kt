package uk.gov.justice.digital.hmpps.keyworker.sar

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationContext
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationContextHolder
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationPolicy
import uk.gov.justice.digital.hmpps.keyworker.config.set
import uk.gov.justice.digital.hmpps.keyworker.domain.Allocation
import uk.gov.justice.digital.hmpps.keyworker.domain.AllocationRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.Policy
import uk.gov.justice.digital.hmpps.keyworker.domain.PolicyRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.asCodedDescription
import uk.gov.justice.digital.hmpps.keyworker.integration.prisonapi.PrisonApiClient
import java.time.LocalDate

@Service
class SubjectAccessRequest(
  private val ach: AllocationContextHolder,
  private val allocationRepository: AllocationRepository,
  private val policyRepository: PolicyRepository,
  private val prisonApi: PrisonApiClient,
) {
  fun getSarContent(
    prn: String,
    fromDate: LocalDate?,
    toDate: LocalDate?,
  ): SubjectAccessResponse? {
    val allocations =
      AllocationPolicy.entries
        .map {
          AllocationContext.get().copy(policy = it).set()
          allocationRepository.findAllocationsForSar(prn, fromDate?.atStartOfDay(), toDate?.atStartOfDay()?.plusDays(1))
        }.flatten()
    val policyMap = policyRepository.findAll().associateBy(Policy::code)
    val staffMap: Map<Long, StaffMember> =
      prisonApi.getStaffSummariesFromIds(allocations.map { it.staffId }.toSet()).associate {
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
    allocationReason.description(),
    deallocationReason?.description(),
    getStaff(staffId),
    getPolicy(policy).asCodedDescription(),
  )
}
