package uk.gov.justice.digital.hmpps.keyworker.services

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.keyworker.events.ComplexityOfNeedLevel
import uk.gov.justice.digital.hmpps.keyworker.repository.LegacyPrisonConfigurationRepository

@Service
@ConditionalOnExpression("T(org.apache.commons.lang3.StringUtils).isNotBlank('\${complexity_of_need_uri}')")
class ComplexityOfNeedService(
  private val complexityOfNeedGateway: ComplexityOfNeedGateway,
  private val prisonSupportedRepository: LegacyPrisonConfigurationRepository,
) : RemoveHighComplexityOfNeed {
  override fun removeOffendersWithHighComplexityOfNeed(
    prisonId: String,
    offenders: Set<String>,
  ): Set<String> {
    if (!prisonSupportedRepository.findByPrisonCode(prisonId).map { it.hasPrisonersWithHighComplexityNeeds() }.orElse(false)) {
      return offenders
    }

    val complexOffenders =
      complexityOfNeedGateway
        .getOffendersWithMeasuredComplexityOfNeed(offenders)
        .filter { it.level == ComplexityOfNeedLevel.HIGH }
        .map { it.offenderNo }
        .toSet()

    return offenders.filter { !complexOffenders.contains(it) }.toSet()
  }
}
