package uk.gov.justice.digital.hmpps.keyworker.events

import java.time.LocalDateTime

interface DomainEvent {
  val eventType: String
  val apiEndpoint: String
  val eventOccurred: LocalDateTime
}
