package uk.gov.justice.digital.hmpps.keyworker.integration

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.UUID

@Component
class ManageUsersClient(
  @Qualifier("manageUsersApiWebClient") private val webClient: WebClient,
) {
  fun getUserDetails(username: String): UserDetails? = getUserDetailsMono(username).block()

  fun getUsersDetails(usernames: Set<String>): List<UserDetails> =
    if (usernames.isEmpty()) {
      emptyList()
    } else {
      Flux
        .fromIterable(usernames)
        .flatMap({ getUserDetailsMono(it) }, 10)
        .collectList()
        .block()!!
    }

  private fun getUserDetailsMono(username: String): Mono<UserDetails> =
    webClient
      .get()
      .uri("/users/{username}", username)
      .exchangeToMono { res ->
        when (res.statusCode()) {
          HttpStatus.NOT_FOUND -> Mono.empty()
          HttpStatus.OK -> res.bodyToMono<UserDetails>()
          else -> res.createError()
        }
      }.retryRequestOnTransientException()
}

data class UserDetails(
  val username: String,
  val active: Boolean,
  val name: String,
  val authSource: String,
  val userId: String,
  val uuid: UUID?,
  val activeCaseLoadId: String?,
)
