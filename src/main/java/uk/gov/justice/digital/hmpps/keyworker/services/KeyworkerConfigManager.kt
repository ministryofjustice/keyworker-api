package uk.gov.justice.digital.hmpps.keyworker.services

import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.keyworker.domain.KeyworkerAllocationRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.KeyworkerConfig
import uk.gov.justice.digital.hmpps.keyworker.domain.KeyworkerConfigRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceDataDomain.DEALLOCATION_REASON
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceDataRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.getKeyworkerStatus
import uk.gov.justice.digital.hmpps.keyworker.domain.of
import uk.gov.justice.digital.hmpps.keyworker.dto.KeyworkerConfigRequest
import uk.gov.justice.digital.hmpps.keyworker.model.DeallocationReason.KEYWORKER_STATUS_CHANGE

@Transactional
@Service
class KeyworkerConfigManager(
  private val referenceDataRepository: ReferenceDataRepository,
  private val keyworkerConfigRepository: KeyworkerConfigRepository,
  private val allocationRepository: KeyworkerAllocationRepository,
) {
  fun configure(
    prisonCode: String,
    staffId: Long,
    request: KeyworkerConfigRequest,
  ) {
    keyworkerConfigRepository.save(
      keyworkerConfigRepository.findByIdOrNull(staffId)?.update(request) ?: request.asConfig(staffId),
    )

    if (request.deactivateActiveAllocations) {
      val deallocationReason =
        requireNotNull(referenceDataRepository.findByKey(DEALLOCATION_REASON of KEYWORKER_STATUS_CHANGE.reasonCode))
      allocationRepository.findActiveForPrisonStaff(prisonCode, staffId).forEach {
        it.deallocate(deallocationReason)
      }
    }
  }

  private fun KeyworkerConfigRequest.asConfig(staffId: Long) =
    KeyworkerConfig(
      referenceDataRepository.getKeyworkerStatus(status),
      capacity,
      !removeFromAutoAllocation,
      reactivateOn,
      staffId,
    )

  private fun KeyworkerConfig.update(request: KeyworkerConfigRequest) =
    apply {
      status = referenceDataRepository.getKeyworkerStatus(request.status)
      capacity = request.capacity
      allowAutoAllocation = !request.removeFromAutoAllocation
      reactivateOn = request.reactivateOn
    }
}
