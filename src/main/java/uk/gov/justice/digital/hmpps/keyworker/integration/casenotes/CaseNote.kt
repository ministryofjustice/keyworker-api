package uk.gov.justice.digital.hmpps.keyworker.integration.casenotes

import com.fasterxml.jackson.annotation.JsonAlias
import java.time.LocalDateTime
import java.util.UUID

data class CaseNote(
  val id: UUID,
  val type: String,
  val subType: String,
  @JsonAlias("occurrenceDateTime")
  val occurredAt: LocalDateTime,
  @JsonAlias("offenderIdentifier")
  val personIdentifier: String,
  @JsonAlias("authorUserId")
  val staffId: String,
  @JsonAlias("locationId")
  val prisonCode: String,
  @JsonAlias("creationDateTime")
  val createdAt: LocalDateTime,
) {
  companion object {
    const val KW_TYPE = "KA"
    const val SESSION_SUBTYPE = "KS"
    const val ENTRY_SUBTYPE = "KE"
    const val TRANSFER_TYPE = "TRANSFER"
  }
}
