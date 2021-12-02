package uk.gov.justice.digital.hmpps.keyworker.services

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.stereotype.Service

@Service
@ConditionalOnExpression("T(org.apache.commons.lang3.StringUtils).isBlank('\${complexity_of_need_uri}')")
class StubComplexityOfNeed : ComplexityOfNeed {
  override fun removeOffendersWithHighComplexityOfNeed(prisonId: String, offenders: Set<String>): Set<String> =
    offenders

  override fun isComplexPrison(prisonId: String): Boolean = false
}
