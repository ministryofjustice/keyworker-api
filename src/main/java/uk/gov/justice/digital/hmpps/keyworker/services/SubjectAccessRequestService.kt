package uk.gov.justice.digital.hmpps.keyworker.services

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.keyworker.sar.SubjectAccessRequest
import uk.gov.justice.hmpps.kotlin.sar.HmppsPrisonSubjectAccessRequestService
import uk.gov.justice.hmpps.kotlin.sar.HmppsSubjectAccessRequestContent
import java.time.LocalDate

@Service
class SubjectAccessRequestService(
  private val subjectAccessRequest: SubjectAccessRequest,
) : HmppsPrisonSubjectAccessRequestService {
  override fun getPrisonContentFor(
    prn: String,
    fromDate: LocalDate?,
    toDate: LocalDate?,
  ): HmppsSubjectAccessRequestContent? {
    val sarResponse = subjectAccessRequest.getSarContent(prn, fromDate, toDate)
    return sarResponse?.let { HmppsSubjectAccessRequestContent(it.content) }
  }
}
