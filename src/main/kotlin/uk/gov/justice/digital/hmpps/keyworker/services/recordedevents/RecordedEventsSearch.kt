package uk.gov.justice.digital.hmpps.keyworker.services.recordedevents

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.keyworker.domain.CaseNoteRecordedEventRepository
import uk.gov.justice.digital.hmpps.keyworker.domain.CaseNoteTypeKey
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceData
import uk.gov.justice.digital.hmpps.keyworker.domain.asCodedDescription
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNotesApiClient
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.SearchCaseNotes
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.TypeSubTypeRequest
import uk.gov.justice.digital.hmpps.keyworker.integration.prisonersearch.Prisoner
import uk.gov.justice.digital.hmpps.keyworker.integration.prisonersearch.PrisonerSearchClient
import uk.gov.justice.digital.hmpps.keyworker.model.RecordedEventAmendment
import uk.gov.justice.digital.hmpps.keyworker.model.RecordedEventPrisoner
import uk.gov.justice.digital.hmpps.keyworker.model.RecordedEventRequest
import uk.gov.justice.digital.hmpps.keyworker.model.RecordedEventResponse
import uk.gov.justice.digital.hmpps.keyworker.model.StaffRecordedEvent

@Service
class RecordedEventsSearch(
  private val cnReRepository: CaseNoteRecordedEventRepository,
  private val caseNoteApi: CaseNotesApiClient,
  private val prisonerSearch: PrisonerSearchClient,
) {
  fun searchForAuthor(
    prisonCode: String,
    staffId: Long,
    request: RecordedEventRequest,
  ): RecordedEventResponse =
    cnReRepository.findAll().takeIf { it.isNotEmpty() }?.let { types ->
      val typeMap = types.associateBy { it.key }
      val caseNotes = caseNoteApi.searchAuthorNotes(prisonCode, staffId, request.searchCaseNotes(typeMap.keys)).content
      val prisoners =
        prisonerSearch
          .findPrisonerDetails(caseNotes.map { it.personIdentifier }.toSet())
          .associateBy { it.prisonerNumber }
      RecordedEventResponse(
        caseNotes.map { cn ->
          cn.asRecordedEvent(
            { requireNotNull(prisoners[it]) },
            { type, subtype -> requireNotNull(typeMap[CaseNoteTypeKey(type, subtype)]).type },
          )
        },
      )
    } ?: RecordedEventResponse(emptyList())

  private fun RecordedEventRequest.searchCaseNotes(keys: Set<CaseNoteTypeKey>): SearchCaseNotes =
    SearchCaseNotes(
      keys
        .groupBy({ it.cnType }, { it.cnSubType })
        .map { (key, value) -> TypeSubTypeRequest(key, value.toSet()) }
        .toSet(),
      from?.atStartOfDay(),
      to?.plusDays(1)?.atStartOfDay(),
    )

  private fun CaseNote.asRecordedEvent(
    prisoner: (String) -> Prisoner,
    rd: (String, String) -> ReferenceData,
  ) = StaffRecordedEvent(
    prisoner(personIdentifier).forRecordedEvent(),
    rd(type, subType).asCodedDescription(),
    createdAt,
    occurredAt,
    text,
    amendments.map { RecordedEventAmendment(it.createdAt, it.authorName, it.additionalNoteText) },
  )

  private fun Prisoner.forRecordedEvent() = RecordedEventPrisoner(prisonerNumber, firstName, lastName)
}
