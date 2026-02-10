package uk.gov.justice.digital.hmpps.keyworker.integration.events.offender

import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationContext
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationPolicy
import uk.gov.justice.digital.hmpps.keyworker.config.set
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
  private val jsonMapper: JsonMapper,
  private val complexityOfNeed: ComplexityOfNeedApiClient,
  private val deallocationService: DeallocationService,
) {
  fun onComplexityChange(message: String) {
    val event = jsonMapper.readValue<ComplexityOfNeedChange>(message)
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
          AllocationContext
            .get()
            .copy(username = username, requestAt = it.updatedTimeStamp ?: it.createdTimeStamp ?: now(), policy = null)
            .set()
        }
      }
    AllocationPolicy.entries.forEach { policy ->
      AllocationContext.get().copy(policy = policy).set()
      deallocationService.deallocateExpiredAllocations(
        "",
        event.offenderNo,
        DeallocationReason.CHANGE_IN_COMPLEXITY_OF_NEED,
      )
    }
  }
}
