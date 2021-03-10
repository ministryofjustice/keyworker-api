package uk.gov.justice.digital.hmpps.keyworker.events

data class Message(val Message: String, val MessageAttributes: MessageAttributes)
data class MessageAttributes(val eventType: Attribute)
data class Attribute(val Value: String)
