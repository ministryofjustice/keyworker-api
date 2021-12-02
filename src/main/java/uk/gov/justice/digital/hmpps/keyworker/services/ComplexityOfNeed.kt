package uk.gov.justice.digital.hmpps.keyworker.services

interface ComplexityOfNeed {
  fun removeOffendersWithHighComplexityOfNeed(prisonId: String, offenders: Set<String>): Set<String>
  fun isComplexPrison(prisonId: String): Boolean
}
