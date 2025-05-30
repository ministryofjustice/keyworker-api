package uk.gov.justice.digital.hmpps.keyworker.integration.nomisuserroles

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import uk.gov.justice.digital.hmpps.keyworker.integration.retryRequestOnTransientException

@Component
class NomisUserRolesApiClient(
  @Qualifier("nomisUserRolesWebClient") private val webClient: WebClient,
) {
  fun setStaffRole(
    prisonCode: String,
    staffId: Long,
    role: String,
    request: StaffJobClassificationRequest,
  ): StaffJobClassification? =
    webClient
      .put()
      .uri(STAFF_ROLE_URL, prisonCode, staffId, role)
      .bodyValue(request)
      .exchangeToMono { res ->
        when (res.statusCode()) {
          HttpStatus.OK -> res.bodyToMono<StaffJobClassification>()
          else -> res.createError()
        }
      }.retryRequestOnTransientException()
      .block()

  companion object {
    const val STAFF_ROLE_URL = "/agency/{agencyId}/staff-members/{staffId}/staff-role/{staffRole}"
  }
}
