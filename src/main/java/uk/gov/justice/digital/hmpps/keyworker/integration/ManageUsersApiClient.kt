package uk.gov.justice.digital.hmpps.keyworker.integration

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationContext.Companion.OMIC_ADMIN_USERNAME
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationContext.Companion.SYSTEM_USERNAME
import java.util.UUID

@Component
class ManageUsersClient(
  @Qualifier("manageUsersApiWebClient") private val webClient: WebClient,
) {
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
    if (username == SYSTEM_USERNAME || username == OMIC_ADMIN_USERNAME) {
      Mono.just(username.asSystemUser())
    } else {
      webClient
        .get()
        .uri("/users/{username}", username)
        .exchangeToMono { res ->
          when (res.statusCode()) {
            HttpStatus.NOT_FOUND -> Mono.just(username.asSystemUser())
            HttpStatus.OK -> res.bodyToMono<UserDetails>()
            else -> res.createError()
          }
        }.retryRequestOnTransientException()
    }
}

private fun String.asSystemUser() = UserDetails(this, true, "User $this", "DPS", "0", null, null)

data class UserDetails(
  val username: String,
  val active: Boolean,
  val name: String,
  val authSource: String,
  val userId: String,
  val uuid: UUID?,
  val activeCaseLoadId: String?,
)
