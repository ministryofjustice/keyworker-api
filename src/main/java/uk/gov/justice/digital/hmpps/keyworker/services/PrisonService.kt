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
  private val prisonRegister: PrisonRegisterClient,
) {
  fun getPrisonKeyworkerConfig(prisonCode: String) = prisonConfig.findByIdOrNull(prisonCode).prisonConfig()

  fun findPrisons(ids: Set<String>): List<Prison> = prisonRegister.findPrisons(ids)
}

data class PrisonsByIdsRequest(
  val prisonIds: Set<String>,
)

data class Prison(
  val prisonId: String,
  val prisonName: String,
)

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
