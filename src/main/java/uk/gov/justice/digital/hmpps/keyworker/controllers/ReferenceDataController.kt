package uk.gov.justice.digital.hmpps.keyworker.controllers

import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.keyworker.config.REFERENCE_DATA
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceDataDomain
import uk.gov.justice.digital.hmpps.keyworker.dto.CodedDescription
import uk.gov.justice.digital.hmpps.keyworker.services.RetrieveReferenceData

@Tag(name = REFERENCE_DATA)
@RestController
@RequestMapping("/reference-data/{domain}")
class ReferenceDataController(
  val referenceData: RetrieveReferenceData,
) {
  @PreAuthorize("hasAnyRole('${Roles.KEYWORKER_RO}', '${Roles.KEYWORKER_RW}')")
  @GetMapping
  fun findReferenceDataForDomain(
    @Parameter(
      description = "The reference data domain required. This is case insensitive.",
      schema =
        Schema(
          type = "string",
          allowableValues = ["keyworker-status", "allocation-reason", "deallocation-reason"],
        ),
    )
    @PathVariable domain: String,
  ): List<CodedDescription> = referenceData.findAllByDomain(ReferenceDataDomain.of(domain))
}
