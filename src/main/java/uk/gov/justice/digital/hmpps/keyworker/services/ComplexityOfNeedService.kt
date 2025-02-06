package uk.gov.justice.digital.hmpps.keyworker.services

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.keyworker.events.ComplexityOfNeedLevel
import uk.gov.justice.digital.hmpps.keyworker.repository.PrisonSupportedRepository

@Service
@ConditionalOnExpression("T(org.apache.commons.lang3.StringUtils).isNotBlank('\${complexity_of_need_uri}')")
class ComplexityOfNeedService(
  private val complexityOfNeedGateway: ComplexityOfNeedGateway,
  private val prisonSupportedRepository: PrisonSupportedRepository,
) : ComplexityOfNeed {
  override fun removeOffendersWithHighComplexityOfNeed(
    prisonId: String,
    offenders: Set<String>,
  ): Set<String> {
    if (prisonSupportedRepository.findByIdOrNull(prisonId)?.hasPrisonersWithHighComplexityNeeds() != true) return offenders

    val complexOffenders =
      complexityOfNeedGateway
        .getOffendersWithMeasuredComplexityOfNeed(offenders)
        .filter { it.level == ComplexityOfNeedLevel.HIGH }
        .map { it.offenderNo }
        .toSet()

    return offenders
      .filter {
        !complexOffenders.contains(it)
      }.toSet()
  }
}
