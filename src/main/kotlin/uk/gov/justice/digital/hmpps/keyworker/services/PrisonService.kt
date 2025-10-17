package uk.gov.justice.digital.hmpps.keyworker.services

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationContext
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationPolicy
import uk.gov.justice.digital.hmpps.keyworker.config.set
import uk.gov.justice.digital.hmpps.keyworker.domain.PrisonConfiguration
import uk.gov.justice.digital.hmpps.keyworker.domain.PrisonConfigurationRepository
import uk.gov.justice.digital.hmpps.keyworker.integration.prisonregister.PrisonRegisterClient
import uk.gov.justice.digital.hmpps.keyworker.model.prison.PolicyEnabled
import uk.gov.justice.digital.hmpps.keyworker.model.prison.PrisonConfigRequest
import uk.gov.justice.digital.hmpps.keyworker.model.prison.PrisonConfigResponse
import uk.gov.justice.digital.hmpps.keyworker.model.prison.PrisonPolicies

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

  fun getPrisonPolicies(prisonCode: String): PrisonPolicies {
    val activePolicies = prisonConfig.findEnabledPrisonPolicies(prisonCode)
    return PrisonPolicies(
      AllocationPolicy.entries.map { PolicyEnabled(it, it.name in activePolicies) }.toSet(),
    )
  }

  @Transactional(propagation = Propagation.NEVER)
  fun setPrisonPolicies(
    prisonCode: String,
    prison: PrisonPolicies,
  ): PrisonPolicies {
    checkNotNull(prisonRegister.findPrison(prisonCode)) { "Provided prison code was invalid" }
    val configs =
      prisonConfig
        .findConfigurationsForPolicies(prisonCode, prison.policies.map { it.policy.name }.toSet())
        .associateBy { it.policy }
    return prison.policies
      .map { pe ->
        AllocationContext.get().copy(policy = pe.policy).set()
        prisonConfig.save(
          configs[pe.policy.name]?.apply {
            enabled = pe.enabled
          } ?: PrisonConfiguration.default(prisonCode, pe.policy).apply { enabled = pe.enabled },
        )
      }.let { p ->
        PrisonPolicies(p.map { PolicyEnabled(AllocationPolicy.of(it.policy)!!, it.enabled) }.toSet())
      }
  }
}

data class PrisonsByIdsRequest(
  val prisonIds: Set<String>,
)

data class Prison(
  val prisonId: String,
  val prisonName: String,
) {
  companion object {
    const val CODE_PATTERN = "([A-Z]{3}|CADM_I|ZZGHI|undefined)"
  }
}

private fun PrisonConfiguration.response(): PrisonConfigResponse =
  PrisonConfigResponse(
    enabled,
    hasPrisonersWithHighComplexityNeeds,
    allowAutoAllocation,
    capacity,
    frequencyInWeeks,
    allocationOrder,
  )

private fun PrisonConfigRequest.asPrisonConfig(prisonCode: String) =
  PrisonConfiguration(
    prisonCode,
    isEnabled,
    allowAutoAllocation,
    capacity,
    frequencyInWeeks,
    hasPrisonersWithHighComplexityNeeds ?: false,
    allocationOrder,
    AllocationContext.get().requiredPolicy().name,
  )

fun Prison?.orDefault(code: String) = this ?: Prison(code, code)
