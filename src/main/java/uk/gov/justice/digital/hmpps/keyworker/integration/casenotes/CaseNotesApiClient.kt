package uk.gov.justice.digital.hmpps.keyworker.integration.casenotes

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.BodyInserters.fromValue
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.keyworker.integration.retryRequestOnTransientException
import java.util.UUID

@Component
class CaseNotesApiClient(
  @Qualifier("caseNotesApiWebClient") private val webClient: WebClient,
) {
  fun getUsageByPersonIdentifier(usage: UsageByPersonIdentifierRequest): NoteUsageResponse<UsageByPersonIdentifierResponse> =
    webClient
      .post()
      .uri("/case-notes/usage")
      .bodyValue(usage)
      .exchangeToMono { res ->
        when (res.statusCode()) {
          HttpStatus.OK -> res.bodyToMono<NoteUsageResponse<UsageByPersonIdentifierResponse>>()
          else -> res.createError()
        }
      }.retryRequestOnTransientException()
      .block()!!

  fun getUsageByStaffIds(usage: UsageByAuthorIdRequest): NoteUsageResponse<UsageByAuthorIdResponse> =
    if (usage.authorIds.isEmpty()) {
      NoteUsageResponse(emptyMap())
    } else {
      webClient
        .post()
        .uri("/case-notes/staff-usage")
        .body(fromValue(usage))
        .exchangeToMono { res ->
          when (res.statusCode()) {
            HttpStatus.OK -> res.bodyToMono<NoteUsageResponse<UsageByAuthorIdResponse>>()
            else -> res.createError()
          }
        }.retryRequestOnTransientException()
        .block()!!
    }

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

  fun getCaseNotesOfInterest(personIdentifier: String): CaseNotes =
    webClient
      .post()
      .uri("/search/case-notes/$personIdentifier")
      .bodyValue(SearchCaseNotes())
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
