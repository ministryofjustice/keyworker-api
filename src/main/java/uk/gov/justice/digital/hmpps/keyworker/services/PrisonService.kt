package uk.gov.justice.digital.hmpps.keyworker.services

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import uk.gov.justice.digital.hmpps.keyworker.domain.PrisonConfig
import uk.gov.justice.digital.hmpps.keyworker.domain.PrisonConfigRepository
import uk.gov.justice.digital.hmpps.keyworker.dto.Agency
import uk.gov.justice.digital.hmpps.keyworker.dto.PrisonKeyworkerConfiguration
import uk.gov.justice.digital.hmpps.keyworker.dto.PrisonKeyworkerConfiguration.Companion.NOT_CONFIGURED
import uk.gov.justice.digital.hmpps.keyworker.integration.retryRequestOnTransientException

@Service
class PrisonService(
  private val prisonConfig: PrisonConfigRepository,
  @Qualifier("prisonApiWebClient") private val webClient: WebClient,
) {
  fun getPrisonKeyworkerConfig(prisonCode: String) = prisonConfig.findByIdOrNull(prisonCode).prisonConfig()

  fun findAllPrisons(): List<Agency> =
    webClient
      .get()
      .uri("/agencies/prisons")
      .retrieve()
      .bodyToMono<List<Agency>>()
      .retryRequestOnTransientException()
      .block()!!
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
