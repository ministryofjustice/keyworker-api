package uk.gov.justice.digital.hmpps.keyworker.services

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.keyworker.integration.ManageUsersClient
import uk.gov.justice.digital.hmpps.keyworker.integration.PrisonApiClient

@Service
class VerifyKeyworkerStatus(
  private val manageUsersClient: ManageUsersClient,
  private val prisonApiClient: PrisonApiClient,
) {
  fun isKeyworker(
    username: String,
    prisonCode: String,
  ): UsernameKeyworker {
    val userDetails = checkNotNull(manageUsersClient.getUserDetails(username)) { "Username not recognised" }
    val nsr =
      userDetails.userId
        .toLongOrNull()
        ?.let {
          prisonApiClient.getKeyworkerForPrison(prisonCode, it)
        }?.takeIf { !it.isExpired() }

    return UsernameKeyworker(username, nsr != null)
  }
}

data class UsernameKeyworker(
  val username: String,
  val isKeyworker: Boolean,
)
