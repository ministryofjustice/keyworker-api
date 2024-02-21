package uk.gov.justice.digital.hmpps.keyworker.services

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.keyworker.repository.OffenderKeyworkerRepository
import java.time.LocalDate

class NoContentFoundException : RuntimeException()

@Service
@Transactional(readOnly = true)
class SubjectAccessRequestService(
  val offenderKeyworkerRepository: OffenderKeyworkerRepository,
  val objectMapper: ObjectMapper,
) {
  fun getSubjectAccessRequest(prn: String, fromDate: LocalDate?, toDate: LocalDate?): JsonNode {
    val records = offenderKeyworkerRepository.findByOffenderNo(prn)

    if (records.isEmpty()) throw NoContentFoundException()

    val filteredRecords = if (fromDate != null && toDate != null) {
      objectMapper.readTree(objectMapper.writeValueAsString(records.filter {
        it.assignedDateTime.toLocalDate().isAfter(fromDate.minusDays(1)) && it.assignedDateTime.toLocalDate()
          .isBefore(toDate.plusDays(1))
      }))

    } else if (fromDate != null) {
      objectMapper.readTree(objectMapper.writeValueAsString(records.filter {
        it.assignedDateTime.toLocalDate().isAfter(fromDate.minusDays(1))
      }))

    } else if (toDate != null) {
      objectMapper.readTree(objectMapper.writeValueAsString(records.filter {
        it.assignedDateTime.toLocalDate().isBefore(toDate.plusDays(1))
      }))
    } else {
      objectMapper.readTree(objectMapper.writeValueAsString(records))
    }

    if (filteredRecords.isEmpty) throw NoContentFoundException()

    return objectMapper.readTree(objectMapper.writeValueAsString(filteredRecords))
  }
}
