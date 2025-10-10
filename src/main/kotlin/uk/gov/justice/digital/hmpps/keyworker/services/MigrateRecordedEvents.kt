package uk.gov.justice.digital.hmpps.keyworker.services

import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationContext
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationPolicy
import uk.gov.justice.digital.hmpps.keyworker.config.set
import uk.gov.justice.digital.hmpps.keyworker.domain.CaseNoteRecordedEventRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.CaseNoteTypeKey
import uk.gov.justice.digital.hmpps.keyworker.domain.RecordedEventRepository
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNotesApiClient
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.asRecordedEvent
import uk.gov.justice.digital.hmpps.keyworker.integration.events.domain.CaseNoteMigrationInformation
import uk.gov.justice.digital.hmpps.keyworker.integration.events.domain.HmppsDomainEvent

@Service
class MigrateRecordedEvents(
  private val caseNoteRecordedEventMapping: CaseNoteRecordedEventRepository,
  private val caseNoteApi: CaseNotesApiClient,
  private val recordedEventRepository: RecordedEventRepository,
  private val transactionTemplate: TransactionTemplate,
) {
  fun handle(event: HmppsDomainEvent<CaseNoteMigrationInformation>) {
    val rd =
      AllocationPolicy.entries
        .flatMap { policy ->
          AllocationContext.get().copy(policy = policy).set()
          caseNoteRecordedEventMapping.findAll()
        }.associateBy { it.key }
    event.personReference.nomsNumber()?.also { pi ->
      val notesByPolicy =
        caseNoteApi
          .getCaseNotesOfInterest(pi)
          .content
          .mapNotNull { cn ->
            rd[CaseNoteTypeKey(cn.type, cn.subType)]?.policyCode?.let { it to cn }
          }.groupBy({ AllocationPolicy.valueOf(it.first) }, { it.second })
      recordedEventRepository.deleteAllByPersonIdentifier(pi)
      AllocationPolicy.entries.forEach { policy ->
        AllocationContext.get().copy(policy = policy).set()
        transactionTemplate.execute {
          val trd = caseNoteRecordedEventMapping.findAll().associateBy { it.key }
          notesByPolicy[policy]?.takeIf { it.isNotEmpty() }?.let { notes ->
            val recordedEvents =
              notes.map {
                it.asRecordedEvent { type, subType -> requireNotNull(trd[CaseNoteTypeKey(type, subType)]) }()
              }
            recordedEventRepository.saveAll(recordedEvents)
          }
        }
      }
    }
  }
}
