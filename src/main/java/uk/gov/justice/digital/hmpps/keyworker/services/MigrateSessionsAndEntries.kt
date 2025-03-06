package uk.gov.justice.digital.hmpps.keyworker.services

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.keyworker.domain.KeyworkerEntry
import uk.gov.justice.digital.hmpps.keyworker.domain.KeyworkerEntryRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.KeyworkerSession
import uk.gov.justice.digital.hmpps.keyworker.domain.KeyworkerSessionRepository
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNotesApiClient
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.asKeyworkerInteraction
import uk.gov.justice.digital.hmpps.keyworker.integration.events.CaseNoteMigrationInformation
import uk.gov.justice.digital.hmpps.keyworker.integration.events.HmppsDomainEvent

@Transactional
@Service
class MigrateSessionsAndEntries(
  private val caseNoteApi: CaseNotesApiClient,
  private val ksRepository: KeyworkerSessionRepository,
  private val keRepository: KeyworkerEntryRepository
) {
  fun handle(event: HmppsDomainEvent<CaseNoteMigrationInformation>) {
    event.personReference.nomsNumber()?.also {
      val interactions = caseNoteApi.getAllKeyworkerCaseNotes(it).content.map { it.asKeyworkerInteraction() }
      val sessions = interactions.filterIsInstance<KeyworkerSession>()
      val entries = interactions.filterIsInstance<KeyworkerEntry>()

      val existingSessions = ksRepository.findAllById(sessions.map { it.id }).associateBy { it.id }.toMutableMap()
      val existingEntries = keRepository.findAllById(entries.map { it.id }).associateBy { it.id }.toMutableMap()

      sessions.forEach { session ->
        existingSessions.putIfAbsent(session.id, session)
        existingSessions[session.id]?.occurredAt = session.occurredAt
      }

      entries.forEach { entry ->
        existingEntries.putIfAbsent(entry.id, entry)
        existingEntries[entry.id]?.occurredAt = entry.occurredAt
      }

      ksRepository.saveAll(existingSessions.values)
      keRepository.saveAll(existingEntries.values)
    }
  }
}