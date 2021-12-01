package uk.gov.justice.digital.hmpps.keyworker.controllers

import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiResponse
import io.swagger.annotations.ApiResponses
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.keyworker.dto.ErrorResponse

@Api(tags = ["high-complexity-prisons"])
@RestController
@RequestMapping(value = ["high-complexity-prisons"], produces = [MediaType.APPLICATION_JSON_VALUE])
class HighComplexityPrisonsController(@Value("\${prisons.with.offenders.that.have.complex.needs}") val prisonsWithOffenderComplexityNeeds: Set<String>) {
  @ApiOperation(
    value = "Return prisons which have high complexity prisoners",
    notes = "The codes for prisons which have high complexity prisoners",
    nickname = "getHighComplexityPrisons"
  )
  @ApiResponses(
    value =
    [
      ApiResponse(code = 200, message = "OK", response = String::class, responseContainer = "Set"),
      ApiResponse(code = 400, message = "Invalid request", response = ErrorResponse::class),
      ApiResponse(
        code = 500,
        message = "Unrecoverable error occurred whilst processing request.",
        response = ErrorResponse::class
      )
    ]
  )
  @GetMapping
  fun getHighComplexityPrisons(): Set<String> = prisonsWithOffenderComplexityNeeds
}
