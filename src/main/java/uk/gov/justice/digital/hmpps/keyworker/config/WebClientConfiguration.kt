package uk.gov.justice.digital.hmpps.keyworker.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.oauth2.client.web.reactive.function.client.ServletOAuth2AuthorizedClientExchangeFilterFunction
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ExchangeFilterFunction
import org.springframework.web.reactive.function.client.ExchangeFunction
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.keyworker.utils.UserContext

@Configuration
class WebClientConfiguration(
  @Value("\${elite2.api.uri.root}") private val elite2ApiRootUri: String,
  @Value("\${elite2.uri.root}") private val healthRootUri: String,
  @Value("\${complexity_of_need_uri}") private val complexityOfNeedUri: String
) {
  @Bean
  fun authorizedClientManager(
    clientRegistrationRepository: ClientRegistrationRepository?,
    oAuth2AuthorizedClientService: OAuth2AuthorizedClientService?
  ): OAuth2AuthorizedClientManager {
    val authorizedClientProvider = OAuth2AuthorizedClientProviderBuilder.builder().clientCredentials().build()
    val authorizedClientManager =
      AuthorizedClientServiceOAuth2AuthorizedClientManager(clientRegistrationRepository, oAuth2AuthorizedClientService)
    authorizedClientManager.setAuthorizedClientProvider(authorizedClientProvider)
    return authorizedClientManager
  }

  @Bean
  fun oauth2WebClient(authorizedClientManager: OAuth2AuthorizedClientManager?, builder: WebClient.Builder): WebClient {
    val oauth2Client = ServletOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager)
    oauth2Client.setDefaultClientRegistrationId("elite2-api")
    return builder.baseUrl(elite2ApiRootUri)
      .apply(oauth2Client.oauth2Configuration())
      .build()
  }

  @Bean
  fun complexityOfNeedWebClient(authorizedClientManager: OAuth2AuthorizedClientManager?, builder: WebClient.Builder): WebClient{
    val oauth2Client = ServletOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager)
    oauth2Client.setDefaultClientRegistrationId("elite2-api")

    return builder.baseUrl("$complexityOfNeedUri/v1")
      .apply(oauth2Client.oauth2Configuration())
      .build()
  }

  @Bean
  fun webClient(builder: WebClient.Builder): WebClient {
    return builder
      .baseUrl(elite2ApiRootUri)
      .filter(addAuthHeaderFilterFunction())
      .build()
  }

  @Bean
  fun healthWebClient(builder: WebClient.Builder): WebClient {
    return builder
      .baseUrl(healthRootUri)
      .filter(addAuthHeaderFilterFunction())
      .build()
  }

  @Bean
  fun complexityOfNeedHealthWebClient(builder: WebClient.Builder): WebClient {
    return builder
      .baseUrl("$complexityOfNeedUri/ping")
      .filter(addAuthHeaderFilterFunction())
      .build()
  }

  private fun addAuthHeaderFilterFunction(): ExchangeFilterFunction {
    return ExchangeFilterFunction { request: ClientRequest?, next: ExchangeFunction ->
      val filtered = ClientRequest.from(request)
        .header(HttpHeaders.AUTHORIZATION, UserContext.getAuthToken())
        .build()
      next.exchange(filtered)
    }
  }
}
