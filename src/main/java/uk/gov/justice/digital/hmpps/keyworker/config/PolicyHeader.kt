package uk.gov.justice.digital.hmpps.keyworker.config

import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema

@Parameter(
  name = PolicyHeader.NAME,
  `in` = ParameterIn.HEADER,
  description = """
    Relevant policy for the context e.g. KEY_WORKER or PERSONAL_OFFICER
    """,
  required = false,
  content = [Content(schema = Schema(implementation = String::class))],
)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
internal annotation class PolicyHeader {
  companion object {
    const val NAME = "Policy"
  }
}
