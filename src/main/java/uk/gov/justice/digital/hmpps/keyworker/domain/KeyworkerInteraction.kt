package uk.gov.justice.digital.hmpps.keyworker.domain

import java.time.LocalDateTime
import java.util.UUID

sealed interface KeyworkerInteraction {
  var occurredAt: LocalDateTime
  var personIdentifier: String
  val staffId: Long
  val staffUsername: String
  val prisonCode: String
  val createdAt: LocalDateTime
  var textLength: Int
  var amendmentCount: Int
  val id: UUID
}
