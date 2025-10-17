package uk.gov.justice.digital.hmpps.keyworker.integration.casenotes

import uk.gov.justice.digital.hmpps.keyworker.domain.CaseNoteTypeKey
import java.time.LocalDateTime

data class CaseNotes(
  val content: List<CaseNote>,
)

data class SearchCaseNotes(
  val typeSubTypes: Set<TypeSubTypeRequest>,
  val occurredFrom: LocalDateTime? = null,
  val occurredTo: LocalDateTime? = null,
  val includeSensitive: Boolean = true,
  val page: Int = 1,
  val size: Int = Int.MAX_VALUE,
  val sort: String = "occurredAt,asc",
)

class CaseNotesOfInterest(
  keys: Set<CaseNoteTypeKey>,
) {
  private val entries =
    keys
      .groupBy { it.cnType }
      .map { e -> e.key to e.value.map { it.cnSubType }.toSet() }
      .toMap()

  fun asRequest() = entries.map { (t, s) -> TypeSubTypeRequest(t, s) }.toSet()

  operator fun get(t: String) = entries[t]
}

data class TypeSubTypeRequest(
  val type: String,
  val subTypes: Set<String> = setOf(),
)
