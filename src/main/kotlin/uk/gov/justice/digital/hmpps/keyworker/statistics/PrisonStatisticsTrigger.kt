package uk.gov.justice.digital.hmpps.keyworker.statistics

import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationPolicy
import uk.gov.justice.digital.hmpps.keyworker.domain.PrisonConfiguration
import uk.gov.justice.digital.hmpps.keyworker.domain.PrisonConfigurationRepository
import uk.gov.justice.digital.hmpps.keyworker.integration.events.domain.EventType
import uk.gov.justice.digital.hmpps.keyworker.integration.events.domain.HmppsDomainEvent
import uk.gov.justice.digital.hmpps.keyworker.integration.events.domain.PrisonStatisticsInfo
import uk.gov.justice.digital.hmpps.keyworker.integration.events.domain.publishBatch
import uk.gov.justice.hmpps.sqs.HmppsQueue
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import java.time.LocalDate

@Service
class PrisonStatisticsTrigger(
  private val prisonConfigRepository: PrisonConfigurationRepository,
  private val queueService: HmppsQueueService,
  private val jsonMapper: JsonMapper,
) {
  private val eventQueue: HmppsQueue by lazy {
    queueService.findByQueueId("domaineventsqueue") ?: throw IllegalStateException("Queue not available")
  }

  fun runFor(date: LocalDate) {
    AllocationPolicy.entries.forEach { policy ->
      prisonConfigRepository
        .findEnabledPrisonsForPolicyCode(policy.name)
        .asSequence()
        .map { it.toDomainEvent(date) }
        .chunked(10)
        .forEach { eventQueue.publishBatch(jsonMapper, it) }
    }
  }

  private fun PrisonConfiguration.toDomainEvent(date: LocalDate): HmppsDomainEvent<PrisonStatisticsInfo> =
    HmppsDomainEvent(
      EventType.CalculatePrisonStats.name,
      PrisonStatisticsInfo(code, date, AllocationPolicy.of(policy)!!),
    )
}
