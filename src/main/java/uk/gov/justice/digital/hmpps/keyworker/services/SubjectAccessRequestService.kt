package uk.gov.justice.digital.hmpps.keyworker.services

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.keyworker.repository.OffenderKeyworkerRepository

class NoContentFoundException : RuntimeException()

@Service
@Transactional(readOnly = true)
class SubjectAccessRequestService(
  val offenderKeyworkerRepository: OffenderKeyworkerRepository,
  val objectMapper: ObjectMapper,
) {
  fun getSubjectAccessRequest(prn: String): JsonNode {
    val records = offenderKeyworkerRepository.findByOffenderNo(prn)

    if (records.isEmpty()) throw NoContentFoundException()

    return objectMapper.readTree(objectMapper.writeValueAsString(records))
  }
}
