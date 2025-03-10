package uk.gov.justice.digital.hmpps.keyworker.controllers

import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.keyworker.config.ALLOCATE_KEY_WORKERS
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceDataDomain
import uk.gov.justice.digital.hmpps.keyworker.dto.CodedDescription
import uk.gov.justice.digital.hmpps.keyworker.services.RetrieveReferenceData

@Tag(name = ALLOCATE_KEY_WORKERS)
@RestController
@RequestMapping("/reference-data/{domain}")
class ReferenceDataController(
  val referenceData: RetrieveReferenceData,
) {
  @PreAuthorize("hasAnyRole('${Roles.KEYWORKER_RO}', '${Roles.KEYWORKER_RW}')")
  @GetMapping
  fun findReferenceDataForDomain(
    @PathVariable domain: String,
  ): List<CodedDescription> = referenceData.findAllByDomain(ReferenceDataDomain.of(domain))
}
