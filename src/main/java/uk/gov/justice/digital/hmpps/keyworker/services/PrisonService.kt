package uk.gov.justice.digital.hmpps.keyworker.services

import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.keyworker.domain.PrisonConfig
import uk.gov.justice.digital.hmpps.keyworker.domain.PrisonConfigRepository
import uk.gov.justice.digital.hmpps.keyworker.dto.PrisonKeyworkerConfiguration
import uk.gov.justice.digital.hmpps.keyworker.dto.PrisonKeyworkerConfiguration.Companion.NOT_CONFIGURED

@Service
class PrisonService(
  private val prisonConfig: PrisonConfigRepository,
) {
  fun getPrisonKeyworkerConfig(prisonCode: String) = prisonConfig.findByIdOrNull(prisonCode).prisonConfig()
}

private fun PrisonConfig?.prisonConfig(): PrisonKeyworkerConfiguration =
  this?.let {
    PrisonKeyworkerConfiguration(
      it.migrated,
      it.hasPrisonersWithHighComplexityNeeds,
      it.autoAllocate,
      it.capacityTier1,
      it.capacityTier2,
      it.kwSessionFrequencyInWeeks,
    )
  } ?: NOT_CONFIGURED
