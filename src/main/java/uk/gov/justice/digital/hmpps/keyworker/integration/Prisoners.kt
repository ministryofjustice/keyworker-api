package uk.gov.justice.digital.hmpps.keyworker.integration

import com.fasterxml.jackson.annotation.JsonIgnore
import java.time.LocalDate

data class Prisoners(
  val content: List<Prisoner>,
) {
  private val map = content.associateBy { it.prisonerNumber }

  fun personIdentifiers(): Set<String> = map.keys

  fun findByPersonIdentifier(personIdentifier: String) = map[personIdentifier]

  @JsonIgnore
  val size = map.keys.size

  fun isEmpty() = map.keys.isEmpty()
}

data class Prisoner(
  val prisonerNumber: String,
  val firstName: String,
  val lastName: String,
  val receptionDate: LocalDate,
  val releaseDate: LocalDate?,
  val prisonId: String,
  val prisonName: String,
  val csra: String,
)
