package uk.gov.justice.digital.hmpps.keyworker.services

interface RemoveHighComplexityOfNeed {
  fun removeOffendersWithHighComplexityOfNeed(
    prisonId: String,
    offenders: Set<String>,
  ): Set<String>
}
