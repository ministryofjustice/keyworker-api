package uk.gov.justice.digital.hmpps.keyworker.dto

data class ErrorResponse(
  val status: Int,
  val userMessage: String? = null,
  val developerMessage: String? = null,
)
