package uk.gov.justice.digital.hmpps.keyworker.config

import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@ApiResponses(
  value = [
    ApiResponse(
      responseCode = "400",
      description = "Bad request.",
      content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))]
    ),
    ApiResponse(
      responseCode = "401",
      description = "Unauthorised, requires a valid Oauth2 token",
      content = [Content(schema = Schema(implementation = ErrorResponse::class))],
    ),
    ApiResponse(
      responseCode = "403",
      description = "Forbidden, requires an appropriate role",
      content = [Content(schema = Schema(implementation = ErrorResponse::class))],
    )
  ]
)
internal annotation class StandardAoiErrorResponse()
