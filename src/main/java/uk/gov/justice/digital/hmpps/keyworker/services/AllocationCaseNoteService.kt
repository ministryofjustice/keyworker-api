package uk.gov.justice.digital.hmpps.keyworker.services

import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.keyworker.domain.AllocationCaseNoteRepository
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNotesApiClient
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNotesOfInterest
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.asAllocationCaseNote
import uk.gov.justice.digital.hmpps.keyworker.integration.events.CaseNoteInformation

@Transactional
@Service
class AllocationCaseNoteService(
  private val caseNoteApi: CaseNotesApiClient,
  private val caseNoteRepository: AllocationCaseNoteRepository,
) {
  fun new(personInfo: PersonInformation) {
    getCaseNote(personInfo)?.asAllocationCaseNote()?.also(caseNoteRepository::save)
  }

  fun update(personInfo: PersonInformation) {
    caseNoteRepository.findByIdOrNull(personInfo.info.id)?.also {
      caseNoteRepository.delete(it)
      caseNoteRepository.flush()
      getCaseNote(personInfo)
        ?.takeIf { cn -> cn.isOfInterest() }
        ?.asAllocationCaseNote()
        ?.also(caseNoteRepository::save)
    }
  }

  fun delete(personInfo: PersonInformation) {
    caseNoteRepository.findByIdOrNull(personInfo.info.id)?.also(caseNoteRepository::delete)
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
