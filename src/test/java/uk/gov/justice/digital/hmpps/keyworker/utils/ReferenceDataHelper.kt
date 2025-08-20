package uk.gov.justice.digital.hmpps.keyworker.utils

import uk.gov.justice.digital.hmpps.keyworker.config.AllocationPolicy
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceData
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceDataDomain
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceDataKey
import uk.gov.justice.digital.hmpps.keyworker.domain.of
import uk.gov.justice.digital.hmpps.keyworker.model.AllocationReason
import uk.gov.justice.digital.hmpps.keyworker.model.DeallocationReason
import uk.gov.justice.digital.hmpps.keyworker.model.StaffStatus
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisIdGenerator.newId

object ReferenceDataHelper {
  @JvmStatic
  fun allocationReason(reason: AllocationReason): ReferenceData {
    val description =
      reason.reasonCode.mapIndexed { i, v -> if (i == 0) v.uppercase() else v.toString() }.joinToString("")
    return ReferenceData(
      ReferenceDataDomain.ALLOCATION_REASON of reason.reasonCode,
      description,
      reason.ordinal,
      AllocationPolicy.KEY_WORKER.name,
      newId(),
    )
  }

  @JvmStatic
  fun deallocationReason(reason: DeallocationReason): ReferenceData {
    val description =
      reason.reasonCode.mapIndexed { i, v -> if (i == 0) v.uppercase() else v.toString() }.joinToString("")
    return ReferenceData(
      ReferenceDataDomain.DEALLOCATION_REASON of reason.reasonCode,
      description,
      reason.ordinal,
      AllocationPolicy.KEY_WORKER.name,
      newId(),
    )
  }

  @JvmStatic
  fun keyworkerStatus(status: StaffStatus): ReferenceData {
    val description =
      status.statusCode.mapIndexed { i, v -> if (i == 0) v.uppercase() else v.toString() }.joinToString("")
    return ReferenceData(
      ReferenceDataDomain.STAFF_STATUS of status.name,
      description,
      status.ordinal,
      AllocationPolicy.KEY_WORKER.name,
      newId(),
    )
  }

  @JvmStatic
  fun referenceDataOf(rdKey: ReferenceDataKey): ReferenceData =
    when (rdKey.domain) {
      ReferenceDataDomain.ALLOCATION_REASON -> allocationReason(AllocationReason.get(rdKey.code))
      ReferenceDataDomain.DEALLOCATION_REASON -> deallocationReason(DeallocationReason.get(rdKey.code))
      ReferenceDataDomain.STAFF_STATUS -> keyworkerStatus(StaffStatus.valueOf(rdKey.code))
      else -> throw UnsupportedOperationException("Unsupported reference data domain ${rdKey.domain}")
    }
}
