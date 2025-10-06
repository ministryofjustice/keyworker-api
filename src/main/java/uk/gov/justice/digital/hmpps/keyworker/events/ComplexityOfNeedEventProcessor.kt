package uk.gov.justice.digital.hmpps.keyworker.events

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationContext
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationContextHolder
import uk.gov.justice.digital.hmpps.keyworker.integration.complexityofneed.ComplexityOfNeedApiClient
import uk.gov.justice.digital.hmpps.keyworker.model.DeallocationReason
import uk.gov.justice.digital.hmpps.keyworker.services.DeallocationService
import java.time.LocalDateTime.now

// This event doesn't currently follow the domain event schema
data class ComplexityOfNeedChange(
  val offenderNo: String,
  val level: String,
  val active: Boolean?,
)

@Service
class ComplexityOfNeedEventProcessor(
  private val objectMapper: ObjectMapper,
  private val complexityOfNeed: ComplexityOfNeedApiClient,
  private val deallocationService: DeallocationService,
  private val allocationContextHolder: AllocationContextHolder,
) {
  fun onComplexityChange(message: String) {
    val event = objectMapper.readValue<ComplexityOfNeedChange>(message)
    val complexityLevel = ComplexityOfNeedLevel.valueOf(event.level.uppercase())
    if (event.active != true || complexityLevel != ComplexityOfNeedLevel.HIGH) {
      return
    }

    complexityOfNeed
      .getComplexityOfNeed(setOf(event.offenderNo))
      .firstOrNull {
        it.personIdentifier == event.offenderNo && it.level == ComplexityOfNeedLevel.HIGH
      }?.also {
        it.sourceUser?.also { username ->
          allocationContextHolder.setContext(
            AllocationContext
              .get()
              .copy(username = username, requestAt = it.updatedTimeStamp ?: it.createdTimeStamp ?: now()),
          )
        }
      }
    deallocationService.deallocateExpiredAllocations(
      "",
      event.offenderNo,
      DeallocationReason.CHANGE_IN_COMPLEXITY_OF_NEED,
    )
  }
}
