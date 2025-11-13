package uk.gov.justice.digital.hmpps.keyworker.controllers

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.keyworker.config.PolicyHeader
import uk.gov.justice.digital.hmpps.keyworker.config.REFERENCE_DATA
import uk.gov.justice.digital.hmpps.keyworker.config.StandardAoiErrorResponse
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceDataDomain
import uk.gov.justice.digital.hmpps.keyworker.model.CodedDescription
import uk.gov.justice.digital.hmpps.keyworker.services.RetrieveReferenceData

@Tag(name = REFERENCE_DATA)
@RestController
@RequestMapping("/reference-data/{domain}")
class ReferenceDataController(
  val referenceData: RetrieveReferenceData,
) {
  @Operation(
    summary = "Retrieve reference data for a specific domain.",
    description = "Get ordered available options for the supplied reference data domain"
  )
  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "200",
        description = "Reference returned"
      )
    ]
  )
  @StandardAoiErrorResponse
  @PolicyHeader
  @PreAuthorize("hasRole('${Roles.ALLOCATIONS_UI}')")
  @GetMapping
  fun findReferenceDataForDomain(
    @Parameter(
      description = "The reference data domain required. This is case insensitive.",
      schema =
        Schema(
          type = "string",
          allowableValues = [
            "allocation-reason",
            "deallocation-reason",
            "recorded-entry-type",
            "staff-position",
            "staff-schedule-type",
            "staff-status",
          ],
        ),
    )
    @PathVariable domain: String,
  ): List<CodedDescription> = referenceData.findAllByDomain(ReferenceDataDomain.of(domain))
}
