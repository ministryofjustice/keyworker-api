package uk.gov.justice.digital.hmpps.keyworker.migration

import com.fasterxml.jackson.databind.ObjectMapper
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.retry.RetryPolicy
import org.springframework.retry.backoff.BackOffPolicy
import org.springframework.retry.support.RetryTemplate
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
import software.amazon.awssdk.services.sqs.model.SendMessageResponse
import uk.gov.justice.digital.hmpps.keyworker.controllers.Roles
import uk.gov.justice.digital.hmpps.keyworker.integration.events.EventType
import uk.gov.justice.digital.hmpps.keyworker.integration.events.HmppsDomainEvent
import uk.gov.justice.digital.hmpps.keyworker.integration.events.MessageAttributes
import uk.gov.justice.digital.hmpps.keyworker.integration.events.Notification
import uk.gov.justice.digital.hmpps.keyworker.integration.events.PersonalOfficerMigrationInformation
import uk.gov.justice.hmpps.sqs.DEFAULT_BACKOFF_POLICY
import uk.gov.justice.hmpps.sqs.DEFAULT_RETRY_POLICY
import uk.gov.justice.hmpps.sqs.HmppsQueue
import uk.gov.justice.hmpps.sqs.HmppsQueueService

@RestController
@RequestMapping
class MigrationController(
  private val queueService: HmppsQueueService,
  private val objectMapper: ObjectMapper,
) {
  private val eventQueue: HmppsQueue by lazy {
    queueService.findByQueueId("domaineventsqueue") ?: throw IllegalStateException("Queue not available")
  }

  @Tag(name = "Personal Officer Migration")
  @ResponseStatus(HttpStatus.ACCEPTED)
  @PreAuthorize("hasRole('${Roles.ALLOCATIONS_UI}')")
  @PostMapping("/prisons/{prisonCode}/personal-officer/migrate")
  fun initiateMigration(
    @PathVariable("prisonCode") prisonCode: String,
  ) {
    eventQueue.publish(
      HmppsDomainEvent(
        EventType.MigratePersonalOfficers.name,
        PersonalOfficerMigrationInformation(prisonCode),
      ),
    )
  }

  private fun HmppsQueue.publish(
    event: HmppsDomainEvent<*>,
    retryPolicy: RetryPolicy = DEFAULT_RETRY_POLICY,
    backOffPolicy: BackOffPolicy = DEFAULT_BACKOFF_POLICY,
  ) {
    val retryTemplate =
      RetryTemplate().apply {
        setRetryPolicy(retryPolicy)
        setBackOffPolicy(backOffPolicy)
      }
    val notification = Notification(objectMapper.writeValueAsString(event), MessageAttributes(event.eventType))
    val sendRequest =
      SendMessageRequest
        .builder()
        .queueUrl(queueUrl)
        .messageBody(objectMapper.writeValueAsString(notification))
        .build()

    retryTemplate.execute<SendMessageResponse, Exception> { sqsClient.sendMessage(sendRequest).get() }
  }
}
