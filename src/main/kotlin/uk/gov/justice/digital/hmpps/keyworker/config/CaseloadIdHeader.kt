package uk.gov.justice.digital.hmpps.keyworker.config

import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema

@Parameter(
  name = CaseloadIdHeader.NAME,
  `in` = ParameterIn.HEADER,
  description = """
    Relevant caseload id for the client identity in context e.g. the active caseload id of the logged in user.
    """,
  required = false,
  content = [Content(schema = Schema(implementation = String::class))],
)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
internal annotation class CaseloadIdHeader {
  companion object {
    const val NAME = "CaseloadId"
  }
}
