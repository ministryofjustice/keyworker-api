package uk.gov.justice.digital.hmpps.keyworker.services

import jakarta.persistence.EntityNotFoundException
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.keyworker.client.ManageUsersClient
import uk.gov.justice.digital.hmpps.keyworker.client.PrisonApiClient

@Service
class VerifyKeyworkerStatus(
  private val manageUsersClient: ManageUsersClient,
  private val prisonApiClient: PrisonApiClient,
) {
  fun isKeyworker(
    username: String,
    prisonCode: String,
    role: String = "KW",
  ): UsernameKeyworker {
    val userDetails = checkNotNull(manageUsersClient.getUserDetails(username)) { "Username not recognised" }
    val roleCheck =
      prisonApiClient.staffRoleCheck(userDetails.userId, prisonCode, role)
        ?: throw EntityNotFoundException("Staff not found")
    return UsernameKeyworker(username, roleCheck)
  }
}

data class UsernameKeyworker(val username: String, val isKeyworker: Boolean)
