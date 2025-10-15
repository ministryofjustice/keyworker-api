package uk.gov.justice.digital.hmpps.keyworker.services.recordedevents

import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationContext
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationPolicy
import uk.gov.justice.digital.hmpps.keyworker.config.set
import uk.gov.justice.digital.hmpps.keyworker.domain.CaseNoteRecordedEventRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.CaseNoteTypeKey
import uk.gov.justice.digital.hmpps.keyworker.domain.RecordedEventRepository
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNotesApiClient
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.asRecordedEvent
import uk.gov.justice.digital.hmpps.keyworker.integration.events.domain.CaseNoteInformation

@Service
class RecordedEventService(
  private val cnTypeReTypeRepository: CaseNoteRecordedEventRepository,
  private val caseNoteApi: CaseNotesApiClient,
  private val recordedEventRepository: RecordedEventRepository,
  private val transactionTemplate: TransactionTemplate,
) {
  fun isOfInterest(
    type: String,
    subtype: String,
  ): Boolean =
    AllocationPolicy.entries.any { policy ->
      AllocationContext.get().copy(policy = policy).set()
      cnTypeReTypeRepository.findByKey(CaseNoteTypeKey(type, subtype)) != null
    }

  fun new(personInfo: PersonInformation) {
    getCaseNote(personInfo)?.let { caseNote ->
      cnTypeReTypeRepository.findPolicyFor(caseNote.type, caseNote.subType)?.let { policy ->
        AllocationContext.get().copy(policy = AllocationPolicy.of(policy)).set()
        transactionTemplate.execute {
          caseNote
            .asRecordedEvent { type, subType ->
              requireNotNull(cnTypeReTypeRepository.findByKey(CaseNoteTypeKey(type, subType)))
            }()
            .also(recordedEventRepository::save)
        }
      }
    }
  }

  fun update(personInfo: PersonInformation) {
    recordedEventRepository.findPolicyForId(personInfo.info.id)?.let { policy ->
      AllocationContext.get().copy(policy = AllocationPolicy.of(policy)).set()
      transactionTemplate.execute {
        recordedEventRepository.findByIdOrNull(personInfo.info.id)?.also(recordedEventRepository::delete)
        new(personInfo)
      }
    }
  }

  fun delete(personInfo: PersonInformation) {
    recordedEventRepository.findPolicyForId(personInfo.info.id)?.let { policy ->
      AllocationContext.get().copy(policy = AllocationPolicy.of(policy)).set()
      recordedEventRepository.findByIdOrNull(personInfo.info.id)?.also(recordedEventRepository::delete)
    }
  }

  private fun getCaseNote(personInformation: PersonInformation): CaseNote? =
    caseNoteApi.getCaseNote(personInformation.personIdentifier, personInformation.info.id)
}

data class PersonInformation(
  val personIdentifier: String,
  val info: CaseNoteInformation,
)
