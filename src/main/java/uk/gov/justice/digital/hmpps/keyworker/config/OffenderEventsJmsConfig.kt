package uk.gov.justice.digital.hmpps.keyworker.config

import com.amazon.sqs.javamessaging.ProviderConfiguration
import com.amazon.sqs.javamessaging.SQSConnectionFactory
import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.AnonymousAWSCredentials
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.client.builder.AwsClientBuilder
import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.AmazonSQSClientBuilder
import com.amazonaws.services.sqs.model.CreateQueueRequest
import com.amazonaws.services.sqs.model.QueueAttributeName
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jms.annotation.EnableJms
import org.springframework.jms.config.DefaultJmsListenerContainerFactory
import org.springframework.jms.support.destination.DynamicDestinationResolver
import javax.jms.Session

@Configuration
@ConditionalOnExpression("{'aws', 'localstack'}.contains('\${offender-events-sqs.provider}')")
@EnableJms
class OffenderEventsJmsConfig {
  companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }

  @Bean
  @Suppress("SpringJavaInjectionPointsAutowiringInspection")
  fun jmsListenerContainerFactory(awsSqsClientForOffenderEvents: AmazonSQS): DefaultJmsListenerContainerFactory {
    val factory = DefaultJmsListenerContainerFactory()
    factory.setConnectionFactory(SQSConnectionFactory(ProviderConfiguration(), awsSqsClientForOffenderEvents))
    factory.setDestinationResolver(DynamicDestinationResolver())
    factory.setConcurrency("1")
    factory.setSessionAcknowledgeMode(Session.CLIENT_ACKNOWLEDGE)
    factory.setErrorHandler { t: Throwable? -> log.error("Error caught in jms listener", t) }
    return factory
  }

  @Bean
  @ConditionalOnProperty(name = ["offender-events-sqs.provider"], havingValue = "aws")
  fun awsSqsClientForOffenderEvents(
    @Value("\${offender-events-sqs.aws.access.key.id}") accessKey: String,
    @Value("\${offender-events-sqs.aws.secret.access.key}") secretKey: String,
    @Value("\${offender-events-sqs.endpoint.region}") region: String
  ): AmazonSQS =
    AmazonSQSClientBuilder.standard()
      .withCredentials(AWSStaticCredentialsProvider(BasicAWSCredentials(accessKey, secretKey)))
      .withRegion(region)
      .build()

  @Bean
  @ConditionalOnProperty(name = ["offender-events-sqs.provider"], havingValue = "aws")
  fun awsSqsDlqClientForOffenderEvents(
    @Value("\${offender-events-sqs.aws.dlq.access.key.id}") accessKey: String,
    @Value("\${offender-events-sqs.aws.dlq.secret.access.key}") secretKey: String,
    @Value("\${offender-events-sqs.endpoint.region}") region: String
  ): AmazonSQS =
    AmazonSQSClientBuilder.standard()
      .withCredentials(AWSStaticCredentialsProvider(BasicAWSCredentials(accessKey, secretKey)))
      .withRegion(region)
      .build()

  @Bean("awsSqsClientForOffenderEvents")
  @ConditionalOnProperty(name = ["offender-events-sqs.provider"], havingValue = "localstack")
  fun awsSqsClientForOffenderEventsLocalstack(
    @Value("\${offender-events-sqs.endpoint.url}") serviceEndpoint: String,
    @Value("\${offender-events-sqs.endpoint.region}") region: String
  ): AmazonSQS =
    AmazonSQSClientBuilder.standard()
      .withEndpointConfiguration(AwsClientBuilder.EndpointConfiguration(serviceEndpoint, region))
      .withCredentials(AWSStaticCredentialsProvider(AnonymousAWSCredentials()))
      .build()

  @Bean("awsSqsDlqClientForOffenderEvents")
  @ConditionalOnProperty(name = ["offender-events-sqs.provider"], havingValue = "localstack")
  fun awsSqsDlqClientForOffenderEventsLocalstack(
    @Value("\${offender-events-sqs.endpoint.url}") serviceEndpoint: String,
    @Value("\${offender-events-sqs.endpoint.region}") region: String
  ): AmazonSQS =
    AmazonSQSClientBuilder.standard()
      .withEndpointConfiguration(AwsClientBuilder.EndpointConfiguration(serviceEndpoint, region))
      .withCredentials(AWSStaticCredentialsProvider(AnonymousAWSCredentials()))
      .build()

  @Bean
  @ConditionalOnProperty(name = ["offender-events-sqs.provider"], havingValue = "localstack")
  @Suppress("SpringJavaInjectionPointsAutowiringInspection")
  fun queueUrl(
    @Autowired awsSqsClientForOffenderEvents: AmazonSQS,
    @Value("\${offender-events-sqs.queue.name}") queueName: String,
    @Value("\${offender-events-sqs.dlq.name}") dlqName: String
  ): String {
    val result = awsSqsClientForOffenderEvents.createQueue(CreateQueueRequest(dlqName))
    val dlqArn = awsSqsClientForOffenderEvents.getQueueAttributes(result.queueUrl, listOf(QueueAttributeName.QueueArn.toString()))
    awsSqsClientForOffenderEvents.createQueue(
      CreateQueueRequest(queueName).withAttributes(
        mapOf(
          QueueAttributeName.RedrivePolicy.toString() to
            """{"deadLetterTargetArn":"${dlqArn.attributes["QueueArn"]}","maxReceiveCount":"5"}"""
        )
      )
    )
    return awsSqsClientForOffenderEvents.getQueueUrl(queueName).queueUrl
  }
}
