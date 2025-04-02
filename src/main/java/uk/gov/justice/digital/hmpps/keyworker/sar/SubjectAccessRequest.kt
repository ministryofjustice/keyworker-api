package uk.gov.justice.digital.hmpps.keyworker.sar

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.keyworker.model.AllocationType
import uk.gov.justice.digital.hmpps.keyworker.sar.internal.SarKeyWorkerRepository
import uk.gov.justice.digital.hmpps.keyworker.sar.internal.StaffDetailProvider
import java.time.LocalDate

@Service
class SubjectAccessRequest(
  private val skwr: SarKeyWorkerRepository,
  private val sdp: StaffDetailProvider,
) {
  fun getSarContent(
    prn: String,
    fromDate: LocalDate?,
    toDate: LocalDate?,
  ): SubjectAccessResponse? {
    val kws = skwr.findSarContent(prn, fromDate?.atStartOfDay(), toDate?.plusDays(1)?.atStartOfDay())
    val staffMap: Map<Long, StaffMember> =
      sdp.findStaffSummariesFromIds(kws.map { it.staffId }.toSet()).associate {
        it.staffId to StaffMember(it.firstName, it.lastName)
      }
    val result =
      kws
        .asSequence()
        .map { it.forSarReport { id -> requireNotNull(staffMap[id]) } }
        .sortedByDescending { it.allocatedAt }
        .toList()
    return if (result.isEmpty()) null else SubjectAccessResponse(prn, result)
  }

  private fun uk.gov.justice.digital.hmpps.keyworker.sar.internal.SarKeyWorker.forSarReport(getStaff: (Long) -> StaffMember) =
    SarKeyWorker(
      assignedAt,
      expiredAt,
      prisonCode,
      when (allocationType) {
        AllocationType.AUTO -> "Automatic"
        AllocationType.MANUAL -> "Manual"
        AllocationType.PROVISIONAL -> "Provisional"
      },
      allocationReason.description,
      deallocationReason?.description,
      getStaff(staffId),
    )
}
