package uk.gov.justice.digital.hmpps.keyworker.migration

import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.keyworker.controllers.Roles

@RestController
@RequestMapping
class MigrationController(
  private val mpo: MigratePersonalOfficers,
) {
  @Tag(name = "Personal Officer Migration")
  @ResponseStatus(HttpStatus.ACCEPTED)
  @PreAuthorize("hasRole('${Roles.ALLOCATIONS_UI}')")
  @PostMapping("/prisons/{prisonCode}/personal-officer/migrate")
  fun initiateMigration(
    @PathVariable("prisonCode") prisonCode: String,
  ) {
    mpo.migrate(prisonCode)
  }
}
