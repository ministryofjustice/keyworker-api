package uk.gov.justice.digital.hmpps.keyworker.config

import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientId
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationContext.Companion.SYSTEM_USERNAME
import java.util.concurrent.ConcurrentHashMap

@Component
class CachingClientService(
  private val clientRegistrationRepository: ClientRegistrationRepository,
) : OAuth2AuthorizedClientService {
  private val authorizedClients: MutableMap<OAuth2AuthorizedClientId, OAuth2AuthorizedClient> = ConcurrentHashMap()

  override fun <T : OAuth2AuthorizedClient> loadAuthorizedClient(
    clientRegistrationId: String,
    principalName: String?,
  ): T? =
    clientRegistrationRepository
      .findByRegistrationId(clientRegistrationId)
      ?.let {
        authorizedClients[OAuth2AuthorizedClientId(it.registrationId, SYSTEM_USERNAME)] as? T
      }

  override fun saveAuthorizedClient(
    authorizedClient: OAuth2AuthorizedClient,
    principal: Authentication?,
  ) {
    authorizedClients[
      OAuth2AuthorizedClientId(authorizedClient.clientRegistration.registrationId, SYSTEM_USERNAME),
    ] = authorizedClient
  }

  override fun removeAuthorizedClient(
    clientRegistrationId: String,
    principalName: String?,
  ) {
    // no-op
  }
}
