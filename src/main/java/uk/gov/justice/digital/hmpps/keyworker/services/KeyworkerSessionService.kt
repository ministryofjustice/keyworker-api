package uk.gov.justice.digital.hmpps.keyworker.services

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNotesApiClient
import uk.gov.justice.digital.hmpps.keyworker.integration.events.SessionInformation

@Service
class KeyworkerSessionService(
  private val caseNoteApi: CaseNotesApiClient,
) {
  fun new(personSession: PersonSession) {
  }

  fun update(personSession: PersonSession) {
  }

  fun move(personSession: PersonSession) {
  }

  fun delete(personSession: PersonSession) {
  }

  private fun sessionDetail(personSession: PersonSession): CaseNote =
    caseNoteApi.getCaseNote(personSession.personIdentifier, personSession.sessionInfo.id)
}

data class PersonSession(
  val personIdentifier: String,
  val sessionInfo: SessionInformation,
)
