package uk.gov.justice.digital.hmpps.keyworker.services

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.keyworker.domain.KeyworkerEntry
import uk.gov.justice.digital.hmpps.keyworker.domain.KeyworkerEntryRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.KeyworkerInteraction
import uk.gov.justice.digital.hmpps.keyworker.domain.KeyworkerSession
import uk.gov.justice.digital.hmpps.keyworker.domain.KeyworkerSessionRepository
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote.Companion.ENTRY_SUBTYPE
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote.Companion.SESSION_SUBTYPE
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNotesApiClient
import uk.gov.justice.digital.hmpps.keyworker.integration.events.CaseNoteInformation
import java.util.UUID

@Transactional
@Service
class SessionAndEntryService(
  private val caseNoteApi: CaseNotesApiClient,
  private val ksRepository: KeyworkerSessionRepository,
  private val keRepository: KeyworkerEntryRepository,
) {
  fun new(personInfo: PersonInformation) {
    saveKeyworkerInteraction(getCaseNote(personInfo).asKeyworkerInteraction())
  }

  fun update(personInfo: PersonInformation) {
    updateKeyworkerInteraction(getCaseNote(personInfo)) {
      apply {
        occurredAt = it.occurredAt
      }
    }
  }

  fun move(personInfo: PersonInformation) {
    updateKeyworkerInteraction(getCaseNote(personInfo)) {
      apply {
        personIdentifier = it.personIdentifier
      }
    }
  }

  fun delete(personInfo: PersonInformation) {
    when (personInfo.info.subType) {
      SESSION_SUBTYPE -> ksRepository.deleteById(personInfo.info.id)
      ENTRY_SUBTYPE -> keRepository.deleteById(personInfo.info.id)
    }
  }

  private fun getCaseNote(personInformation: PersonInformation): CaseNote =
    caseNoteApi.getCaseNote(personInformation.personIdentifier, personInformation.info.id)

  private fun saveKeyworkerInteraction(interaction: KeyworkerInteraction) =
    when (interaction) {
      is KeyworkerSession -> ksRepository.save(interaction)
      is KeyworkerEntry -> keRepository.save(interaction)
    }

  private fun getKeyworkerInteraction(interaction: KeyworkerInteraction): KeyworkerInteraction? =
    getRepository(interaction).findByIdOrNull(interaction.id)

  private fun getRepository(interaction: KeyworkerInteraction): JpaRepository<out KeyworkerInteraction, UUID> =
    when (interaction) {
      is KeyworkerSession -> ksRepository
      is KeyworkerEntry -> keRepository
    }

  private fun updateKeyworkerInteraction(
    caseNote: CaseNote,
    block: KeyworkerInteraction.(CaseNote) -> KeyworkerInteraction,
  ) {
    getKeyworkerInteraction(caseNote.asKeyworkerInteraction())?.block(caseNote)?.also(::saveKeyworkerInteraction)
  }
}

data class PersonInformation(
  val personIdentifier: String,
  val info: CaseNoteInformation,
)

fun CaseNote.asKeyworkerInteraction() =
  when (subType) {
    SESSION_SUBTYPE -> KeyworkerSession(occurredAt, personIdentifier, staffId.toLong(), prisonCode, createdAt, id)
    ENTRY_SUBTYPE -> KeyworkerEntry(occurredAt, personIdentifier, staffId.toLong(), prisonCode, createdAt, id)
    else -> throw IllegalArgumentException("Unknown case note sub type")
  }
