package uk.gov.justice.digital.hmpps.keyworker.services

import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationContext
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationContextHolder
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationPolicy
import uk.gov.justice.digital.hmpps.keyworker.domain.CaseNoteRecordedEventRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.CaseNoteTypeKey
import uk.gov.justice.digital.hmpps.keyworker.domain.RecordedEventRepository
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNotesApiClient
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNotesOfInterest
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.asRecordedEvent
import uk.gov.justice.digital.hmpps.keyworker.integration.events.CaseNoteInformation

@Service
class RecordedEventService(
  private val ach: AllocationContextHolder,
  private val cnTypeReTypeRepository: CaseNoteRecordedEventRepository,
  private val caseNoteApi: CaseNotesApiClient,
  private val caseNoteRepository: RecordedEventRepository,
  private val transactionTemplate: TransactionTemplate,
) {
  fun new(personInfo: PersonInformation) {
    getCaseNote(personInfo)?.let { caseNote ->
      cnTypeReTypeRepository.findPolicyFor(caseNote.type, caseNote.subType)?.let { policy ->
        ach.setContext(AllocationContext.get().copy(policy = AllocationPolicy.valueOf(policy)))
        transactionTemplate.execute {
          caseNote
            .asRecordedEvent { type, subType ->
              requireNotNull(cnTypeReTypeRepository.findByKey(CaseNoteTypeKey(type, subType)))
            }()
            .also(caseNoteRepository::save)
        }
      }
    }
  }

  fun update(personInfo: PersonInformation) {
    caseNoteRepository.findPolicyForId(personInfo.info.id)?.let { policy ->
      ach.setContext(AllocationContext.get().copy(policy = AllocationPolicy.valueOf(policy)))
      transactionTemplate.execute {
        caseNoteRepository.findByIdOrNull(personInfo.info.id)?.also(caseNoteRepository::delete)
        new(personInfo)
      }
    }
  }

  fun delete(personInfo: PersonInformation) {
    caseNoteRepository.findPolicyForId(personInfo.info.id)?.let { policy ->
      ach.setContext(AllocationContext.get().copy(policy = AllocationPolicy.valueOf(policy)))
      caseNoteRepository.findByIdOrNull(personInfo.info.id)?.also(caseNoteRepository::delete)
    }
  }

  private fun getCaseNote(personInformation: PersonInformation): CaseNote? =
    caseNoteApi.getCaseNote(personInformation.personIdentifier, personInformation.info.id)
}

data class PersonInformation(
  val personIdentifier: String,
  val info: CaseNoteInformation,
)

private fun CaseNote.isOfInterest(): Boolean {
  val typeSubType = CaseNotesOfInterest[type]
  return typeSubType != null && typeSubType.contains(subType)
}
