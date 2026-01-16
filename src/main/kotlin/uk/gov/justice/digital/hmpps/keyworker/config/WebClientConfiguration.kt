package uk.gov.justice.digital.hmpps.keyworker.config

import io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS
import io.netty.channel.ChannelOption.SO_KEEPALIVE
import io.netty.channel.epoll.EpollChannelOption
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.oauth2.client.web.reactive.function.client.ServletOAuth2AuthorizedClientExchangeFilterFunction
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClient.Builder
import reactor.netty.http.client.HttpClient
import reactor.netty.http.client.HttpClient.create
import uk.gov.justice.hmpps.kotlin.auth.healthWebClient
import java.time.Duration
import java.time.Duration.ofSeconds

@Configuration
class WebClientConfiguration(
  @Value($$"${prison.api.uri.root}") private val prisonApiRootUri: String,
  @Value($$"${manage-users.api.uri.root}") private val manageUsersApiRootUri: String,
  @Value($$"${case-notes.api.uri.root}") private val caseNotesApiRootUri: String,
  @Value($$"${complexity_of_need_uri}") private val complexityOfNeedUri: String,
  @Value($$"${prisoner-search.api.uri.root}") private val prisonerSearchApiRootUri: String,
  @Value($$"${prison-register.api.uri.root}") private val prisonRegisterApiRootUri: String,
  @Value($$"${nomis-user-roles.api.uri.root}") private val nomisUserRolesApiRootUri: String,
  @Value($$"${api.health-timeout-ms}") private val healthTimeoutMs: Long,
) {
  val healthTimeout: Duration = Duration.ofMillis(healthTimeoutMs)

  @Bean
  fun authorizedClientManager(
    clientRegistrationRepository: ClientRegistrationRepository,
    oAuth2AuthorizedClientService: CachingClientService,
  ): OAuth2AuthorizedClientManager {
    val authorizedClientProvider = OAuth2AuthorizedClientProviderBuilder.builder().clientCredentials().build()
    val authorizedClientManager =
      AuthorizedClientServiceOAuth2AuthorizedClientManager(clientRegistrationRepository, oAuth2AuthorizedClientService)
    authorizedClientManager.setAuthorizedClientProvider(authorizedClientProvider)
    return authorizedClientManager
  }

  @Bean
  fun prisonApiWebClient(
    authorizedClientManager: OAuth2AuthorizedClientManager,
    builder: Builder,
  ): WebClient = getOAuthWebClient(authorizedClientManager, builder, "$prisonApiRootUri/api")

  @Bean
  fun prisonApiHealthWebClient(builder: Builder): WebClient = builder.healthWebClient(prisonApiRootUri, healthTimeout)

  @Bean
  fun prisonRegisterApiWebClient(
    authorizedClientManager: OAuth2AuthorizedClientManager,
    builder: Builder,
  ): WebClient = getOAuthWebClient(authorizedClientManager, builder, prisonRegisterApiRootUri)

  @Bean
  fun manageUsersApiWebClient(
    authorizedClientManager: OAuth2AuthorizedClientManager,
    builder: Builder,
  ): WebClient = getOAuthWebClient(authorizedClientManager, builder, manageUsersApiRootUri)

  @Bean
  fun caseNotesApiWebClient(
    authorizedClientManager: OAuth2AuthorizedClientManager,
    builder: Builder,
  ): WebClient = getOAuthWebClient(authorizedClientManager, builder, caseNotesApiRootUri)

  @Bean
  fun prisonerSearchWebClient(
    authorizedClientManager: OAuth2AuthorizedClientManager,
    builder: Builder,
  ): WebClient = getOAuthWebClient(authorizedClientManager, builder, prisonerSearchApiRootUri)

  @Bean
  fun nomisUserRolesWebClient(
    authorizedClientManager: OAuth2AuthorizedClientManager,
    builder: Builder,
  ): WebClient = getOAuthWebClient(authorizedClientManager, builder, nomisUserRolesApiRootUri)

  @Bean
  fun complexityOfNeedWebClient(
    authorizedClientManager: OAuth2AuthorizedClientManager,
    builder: Builder,
  ): WebClient = getOAuthWebClient(authorizedClientManager, builder, "$complexityOfNeedUri/v1")

  @Bean
  fun complexityOfNeedHealthWebClient(builder: Builder): WebClient = builder.healthWebClient(complexityOfNeedUri, healthTimeout)

  private fun getOAuthWebClient(
    authorizedClientManager: OAuth2AuthorizedClientManager,
    builder: Builder,
    rootUri: String,
    timeout: Duration = ofSeconds(2),
  ): WebClient {
    val oauth2Client = ServletOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager)
    oauth2Client.setDefaultClientRegistrationId(DEFAULT_REGISTRATION_ID)
    return builder
      .baseUrl(rootUri)
      .clientConnector(clientConnector(timeout))
      .apply(oauth2Client.oauth2Configuration())
      .build()
  }

  private fun clientConnector(
    timeout: Duration,
    consumer: ((HttpClient) -> Unit)? = null,
  ): ReactorClientHttpConnector {
    val client =
      create()
        .responseTimeout(timeout)
        .option(CONNECT_TIMEOUT_MILLIS, 1000)
        .option(SO_KEEPALIVE, true)
        // this will show a warning on apple (arm) architecture but will work on linux x86 container
        .option(EpollChannelOption.TCP_KEEPINTVL, 60)
    consumer?.also { it.invoke(client) }
    return ReactorClientHttpConnector(client)
  }

  companion object {
    const val DEFAULT_REGISTRATION_ID = "default"
  }
}
