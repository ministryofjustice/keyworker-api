package uk.gov.justice.digital.hmpps.keyworker.services.staff

import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationPolicy
import uk.gov.justice.digital.hmpps.keyworker.integration.events.domain.EventType
import uk.gov.justice.digital.hmpps.keyworker.integration.events.domain.HmppsDomainEvent
import uk.gov.justice.digital.hmpps.keyworker.integration.events.domain.KeyworkerPrisonInformation
import uk.gov.justice.digital.hmpps.keyworker.integration.events.domain.publishBatch
import uk.gov.justice.digital.hmpps.keyworker.services.PrisonService
import uk.gov.justice.hmpps.sqs.HmppsQueue
import uk.gov.justice.hmpps.sqs.HmppsQueueService

@Component
class TriggerKeyworkerSync(
  private val jsonMapper: JsonMapper,
  private val queueService: HmppsQueueService,
  private val prisonService: PrisonService,
) {
  private val eventQueue: HmppsQueue by lazy {
    queueService.findByQueueId("domaineventsqueue") ?: throw IllegalStateException("Queue not available")
  }

  fun syncKeyworkers() {
    prisonService
      .findPolicyEnabledPrisons(AllocationPolicy.KEY_WORKER.name)
      .asSequence()
      .map { it.toDomainEvent() }
      .chunked(10)
      .forEach { eventQueue.publishBatch(jsonMapper, it) }
  }

  private fun String.toDomainEvent(): HmppsDomainEvent<KeyworkerPrisonInformation> =
    HmppsDomainEvent(EventType.SyncKeyworkers.name, KeyworkerPrisonInformation(this))
}
