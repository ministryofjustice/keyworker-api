package uk.gov.justice.digital.hmpps.keyworker.events

import com.fasterxml.jackson.annotation.JsonProperty

data class Message(
  @JsonProperty("Message") val message: String,
  @JsonProperty("MessageAttributes") val messageAttributes: MessageAttributes,
)

data class MessageAttributes(
  val eventType: Attribute,
)

data class Attribute(
  @JsonProperty("Value") val value: String,
)
