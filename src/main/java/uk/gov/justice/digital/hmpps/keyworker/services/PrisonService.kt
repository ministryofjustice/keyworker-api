package uk.gov.justice.digital.hmpps.keyworker.services

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationContext
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationPolicy
import uk.gov.justice.digital.hmpps.keyworker.domain.PrisonConfiguration
import uk.gov.justice.digital.hmpps.keyworker.domain.PrisonConfigurationRepository
import uk.gov.justice.digital.hmpps.keyworker.dto.PrisonConfigRequest
import uk.gov.justice.digital.hmpps.keyworker.dto.PrisonConfigResponse
import uk.gov.justice.digital.hmpps.keyworker.dto.PrisonKeyworkerConfiguration
import uk.gov.justice.digital.hmpps.keyworker.dto.PrisonKeyworkerConfiguration.Companion.NOT_CONFIGURED

@Transactional
@Service
class PrisonService(
  private val prisonConfig: PrisonConfigurationRepository,
  private val prisonRegister: PrisonRegisterClient,
) {
  fun findPolicyEnabledPrisons(policyCode: String): Set<String> =
    AllocationPolicy.of(policyCode)?.name?.let { policyCode ->
      prisonConfig.findEnabledPrisonsForPolicyCode(policyCode).map { it.code }.toSet()
    } ?: emptySet()

  fun getPrisonKeyworkerConfig(prisonCode: String) = prisonConfig.findByCode(prisonCode).prisonKeyworkerConfig()

  fun findPrisons(ids: Set<String>): List<Prison> = prisonRegister.findPrisons(ids)

  fun setPrisonConfig(
    prisonCode: String,
    request: PrisonConfigRequest,
  ): PrisonConfigResponse {
    val config: PrisonConfiguration =
      prisonConfig.findByCode(prisonCode)?.update(request) ?: request.asPrisonConfig(prisonCode)
    prisonConfig.save(config)
    return config.response()
  }

  fun getPrisonConfig(prisonCode: String): PrisonConfigResponse =
    prisonConfig.findByCode(prisonCode)?.response() ?: PrisonConfigResponse.DEFAULT
}

data class PrisonsByIdsRequest(
  val prisonIds: Set<String>,
)

data class Prison(
  val prisonId: String,
  val prisonName: String,
)

private fun PrisonConfiguration?.prisonKeyworkerConfig(): PrisonKeyworkerConfiguration =
  this?.let {
    PrisonKeyworkerConfiguration(
      it.enabled,
      it.hasPrisonersWithHighComplexityNeeds,
      it.allowAutoAllocation,
      it.capacity,
      it.maximumCapacity,
      it.frequencyInWeeks,
    )
  } ?: NOT_CONFIGURED

private fun PrisonConfiguration.response(): PrisonConfigResponse =
  PrisonConfigResponse(
    enabled,
    hasPrisonersWithHighComplexityNeeds,
    allowAutoAllocation,
    maximumCapacity,
    frequencyInWeeks,
  )

private fun PrisonConfigRequest.asPrisonConfig(prisonCode: String) =
  PrisonConfiguration(
    prisonCode,
    isEnabled,
    allowAutoAllocation,
    6,
    capacity,
    frequencyInWeeks,
    hasPrisonersWithHighComplexityNeeds ?: false,
    AllocationContext.get().policy.name,
  )
