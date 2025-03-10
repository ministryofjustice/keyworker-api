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
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.asKeyworkerInteraction
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
    getCaseNote(personInfo).asKeyworkerInteraction()?.let { saveKeyworkerInteraction(it) }
  }

  fun update(personInfo: PersonInformation) {
    updateKeyworkerInteraction(personInfo) {
      apply {
        occurredAt = it.occurredAt
        textLength = it.textLength()
        amendmentCount = it.amendments.size
      }
    }
  }

  fun move(personInfo: PersonInformation) {
    updateKeyworkerInteraction(personInfo) {
      apply {
        personIdentifier = it.personIdentifier
      }
    }
  }

  fun delete(personInfo: PersonInformation) {
    when (personInfo.info.subType) {
      SESSION_SUBTYPE -> ksRepository.deleteById(personInfo.info.id)
      ENTRY_SUBTYPE -> keRepository.deleteById(personInfo.info.id)
      else -> {
        ksRepository.deleteById(personInfo.info.id)
        keRepository.deleteById(personInfo.info.id)
      }
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
    personInfo: PersonInformation,
    block: KeyworkerInteraction.(CaseNote) -> KeyworkerInteraction,
  ) {
    val caseNote = getCaseNote(personInfo)
    val interaction = caseNote.asKeyworkerInteraction()
    val updated =
      interaction?.let {
        getKeyworkerInteraction(it)?.block(caseNote)?.also(::saveKeyworkerInteraction)
      }
    if (updated == null) {
      when (interaction) {
        is KeyworkerSession -> keRepository.deleteById(personInfo.info.id)
        is KeyworkerEntry -> ksRepository.deleteById(personInfo.info.id)
        else -> {
          ksRepository.deleteById(personInfo.info.id)
          keRepository.deleteById(personInfo.info.id)
        }
      }
      // if a session has been changed to an entry or vice versa
      interaction?.let { saveKeyworkerInteraction(it) }
    }
  }
}

data class PersonInformation(
  val personIdentifier: String,
  val info: CaseNoteInformation,
)
