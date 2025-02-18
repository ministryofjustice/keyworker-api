package uk.gov.justice.digital.hmpps.keyworker.integration

import java.time.LocalDate

data class Prisoners(
  val content: List<Prisoner>,
) {
  private val map = content.associateBy { it.prisonerNumber }

  fun personIdentifiers(): Set<String> = map.keys

  fun findByPersonIdentifier(personIdentifier: String) = map[personIdentifier]

  val size = map.size
}

data class Prisoner(
  val prisonerNumber: String,
  val receptionDate: LocalDate,
)
