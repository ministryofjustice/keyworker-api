package uk.gov.justice.digital.hmpps.keyworker.services

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.keyworker.domain.AllocationCaseNoteRepository
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNotesApiClient
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.asAllocationCaseNote
import uk.gov.justice.digital.hmpps.keyworker.integration.events.CaseNoteMigrationInformation
import uk.gov.justice.digital.hmpps.keyworker.integration.events.HmppsDomainEvent

@Transactional
@Service
class MigrateAllocationCaseNotes(
  private val caseNoteApi: CaseNotesApiClient,
  private val caseNoteRepository: AllocationCaseNoteRepository,
) {
  fun handle(event: HmppsDomainEvent<CaseNoteMigrationInformation>) {
    event.personReference.nomsNumber()?.also {
      val ofInterest = caseNoteApi.getCaseNotesOfInterest(it).content.map { cn -> cn.asAllocationCaseNote() }
      caseNoteRepository.deleteAllByPersonIdentifier(it)
      caseNoteRepository.saveAll(ofInterest)
    }
  }
}
