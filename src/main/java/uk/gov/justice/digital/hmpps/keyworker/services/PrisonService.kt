package uk.gov.justice.digital.hmpps.keyworker.services

import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.keyworker.dto.PrisonKeyworkerConfiguration
import uk.gov.justice.digital.hmpps.keyworker.dto.PrisonKeyworkerConfiguration.Companion.NOT_CONFIGURED
import uk.gov.justice.digital.hmpps.keyworker.model.PrisonSupported
import uk.gov.justice.digital.hmpps.keyworker.repository.PrisonSupportedRepository

@Service
class PrisonService(
  val prisonSupported: PrisonSupportedRepository,
) {
  fun getPrisonKeyworkerStatus(prisonCode: String) = prisonSupported.findByIdOrNull(prisonCode).keyworkerStatus()
}

private fun PrisonSupported?.keyworkerStatus(): PrisonKeyworkerConfiguration =
  this?.let {
    PrisonKeyworkerConfiguration(
      it.isMigrated,
      it.hasPrisonersWithHighComplexityNeeds(),
      it.isAutoAllocate,
      it.capacityTier1,
      it.capacityTier2,
      it.kwSessionFrequencyInWeeks,
    )
  } ?: NOT_CONFIGURED
