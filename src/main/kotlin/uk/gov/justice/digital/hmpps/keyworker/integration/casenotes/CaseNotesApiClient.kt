package uk.gov.justice.digital.hmpps.keyworker.integration.casenotes

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.keyworker.integration.retryRequestOnTransientException
import java.util.UUID

@Component
class CaseNotesApiClient(
  @Qualifier("caseNotesApiWebClient") private val webClient: WebClient,
) {
  fun getCaseNote(
    personIdentifier: String,
    id: UUID,
  ): CaseNote? =
    webClient
      .get()
      .uri("/case-notes/$personIdentifier/$id")
      .header("CaseloadId", "***")
      .exchangeToMono { res ->
        when (res.statusCode()) {
          HttpStatus.NOT_FOUND -> Mono.empty()
          HttpStatus.OK -> res.bodyToMono<CaseNote>()
          else -> res.createError()
        }
      }.retryRequestOnTransientException()
      .block()

  fun getCaseNotesOfInterest(
    personIdentifier: String,
    ofInterest: CaseNotesOfInterest,
  ): CaseNotes =
    webClient
      .post()
      .uri("/search/case-notes/$personIdentifier")
      .bodyValue(SearchCaseNotes(ofInterest.asRequest()))
      .exchangeToMono { res ->
        when (res.statusCode()) {
          HttpStatus.OK -> res.bodyToMono<CaseNotes>()
          else -> res.createError()
        }
      }.retryRequestOnTransientException()
      .block()!!

  fun searchAuthorNotes(
    prisonCode: String,
    staffId: Long,
    request: SearchCaseNotes,
  ): CaseNotes =
    webClient
      .post()
      .uri("/search/case-notes/prisons/$prisonCode/authors/$staffId")
      .bodyValue(request)
      .exchangeToMono { res ->
        when (res.statusCode()) {
          HttpStatus.OK -> res.bodyToMono<CaseNotes>()
          else -> res.createError()
        }
      }.retryRequestOnTransientException()
      .block()!!
}
