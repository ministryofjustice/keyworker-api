package uk.gov.justice.digital.hmpps.keyworker.migration

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.keyworker.config.StandardAoiErrorResponse
import uk.gov.justice.digital.hmpps.keyworker.controllers.Roles

@RestController
@RequestMapping
class MigrationController(
  private val mpo: MigratePersonalOfficers,
) {
  @Operation(summary = "Migrate a prison to use the personal offer policy.")
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "202", description = "Migration started."),
    ],
  )
  @StandardAoiErrorResponse
  @Tag(name = "Personal Officer Migration")
  @ResponseStatus(HttpStatus.ACCEPTED)
  @PreAuthorize("hasRole('${Roles.ALLOCATIONS_UI}')")
  @PostMapping("/prisons/{prisonCode}/personal-officer/migrate")
  fun initiateMigration(
    @Parameter(description = "The prison's identifier.", example = "MDI", required = true)
    @PathVariable("prisonCode") prisonCode: String,
  ) {
    mpo.migrate(prisonCode)
  }
}
