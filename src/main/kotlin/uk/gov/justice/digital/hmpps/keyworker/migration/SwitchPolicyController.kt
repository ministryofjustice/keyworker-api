package uk.gov.justice.digital.hmpps.keyworker.migration

import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.keyworker.controllers.Roles

@RestController
@RequestMapping
class SwitchPolicyController(
  private val policy: PolicySwitch,
) {
  @Tag(name = "Personal Officer Switch")
  @PreAuthorize("hasRole('${Roles.ALLOCATIONS_UI}')")
  @PostMapping("/prisons/HVI/switch-policy")
  fun switchHviPolicy() {
    policy.switch("HVI")
  }
}
