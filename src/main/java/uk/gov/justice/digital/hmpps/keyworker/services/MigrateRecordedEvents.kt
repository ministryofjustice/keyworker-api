package uk.gov.justice.digital.hmpps.keyworker.services

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationContext
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationContextHolder
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationPolicy
import uk.gov.justice.digital.hmpps.keyworker.domain.CaseNoteRecordedEventRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.CaseNoteTypeKey
import uk.gov.justice.digital.hmpps.keyworker.domain.RecordedEventRepository
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNotesApiClient
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.asRecordedEvent
import uk.gov.justice.digital.hmpps.keyworker.integration.events.CaseNoteMigrationInformation
import uk.gov.justice.digital.hmpps.keyworker.integration.events.HmppsDomainEvent

@Transactional
@Service
class MigrateRecordedEvents(
  private val ach: AllocationContextHolder,
  private val caseNoteRecordedEventMapping: CaseNoteRecordedEventRepository,
  private val caseNoteApi: CaseNotesApiClient,
  private val caseNoteRepository: RecordedEventRepository,
) {
  fun handle(event: HmppsDomainEvent<CaseNoteMigrationInformation>) {
    val rd = caseNoteRecordedEventMapping.findAll().associateBy { it.key }
    event.personReference.nomsNumber()?.also {
      val ofInterest =
        caseNoteApi.getCaseNotesOfInterest(it).content.map { cn ->
          cn.asRecordedEvent({ type, subtype -> requireNotNull(rd[CaseNoteTypeKey(type, subtype)]) })()
        }
      caseNoteRepository.deleteAllByPersonIdentifier(it)
      val policyMapped = ofInterest.groupBy { re -> AllocationPolicy.valueOf(re.policyCode) }
      policyMapped.forEach { e ->
        ach.setContext(AllocationContext.get().copy(policy = e.key))
        caseNoteRepository.saveAll(e.value)
      }
    }
  }
}
