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
@ConditionalOnExpression("{'aws', 'localstack'}.contains('\${complexity-of-need-sqs.provider}')")
@EnableJms
class ComplexityOfNeedJmsConfig {
  companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }

  @Bean("jmsListenerContainerFactoryForComplexityOfNeed")
  @Suppress("SpringJavaInjectionPointsAutowiringInspection")
  fun jmsListenerContainerFactory(awsSqsClientForComplexityOfNeed: AmazonSQS): DefaultJmsListenerContainerFactory {
    val factory = DefaultJmsListenerContainerFactory()
    factory.setConnectionFactory(SQSConnectionFactory(ProviderConfiguration(), awsSqsClientForComplexityOfNeed))
    factory.setDestinationResolver(DynamicDestinationResolver())
    factory.setConcurrency("1")
    factory.setSessionAcknowledgeMode(Session.CLIENT_ACKNOWLEDGE)
    factory.setErrorHandler { t: Throwable? -> log.error("Error caught in jms listener", t) }
    return factory
  }

  @Bean
  @ConditionalOnProperty(name = ["complexity-of-need-sqs.provider"], havingValue = "aws")
  fun awsSqsClientForComplexityOfNeed(
    @Value("\${complexity-of-need-sqs.aws.access.key.id}") accessKey: String,
    @Value("\${complexity-of-need-sqs.aws.secret.access.key}") secretKey: String,
    @Value("\${complexity-of-need-sqs.endpoint.region}") region: String
  ): AmazonSQS =
    AmazonSQSClientBuilder.standard()
      .withCredentials(AWSStaticCredentialsProvider(BasicAWSCredentials(accessKey, secretKey)))
      .withRegion(region)
      .build()

  @Bean
  @ConditionalOnProperty(name = ["complexity-of-need-sqs.provider"], havingValue = "aws")
  fun awsSqsDlqClientForComplexityOfNeed(
    @Value("\${complexity-of-need-sqs.aws.dlq.access.key.id}") accessKey: String,
    @Value("\${complexity-of-need-sqs.aws.dlq.secret.access.key}") secretKey: String,
    @Value("\${complexity-of-need-sqs.endpoint.region}") region: String
  ): AmazonSQS =
    AmazonSQSClientBuilder.standard()
      .withCredentials(AWSStaticCredentialsProvider(BasicAWSCredentials(accessKey, secretKey)))
      .withRegion(region)
      .build()

  @Bean("awsSqsClientForComplexityOfNeed")
  @ConditionalOnProperty(name = ["complexity-of-need-sqs.provider"], havingValue = "localstack")
  fun awsSqsClientForOffenderEventsLocalstack(
    @Value("\${complexity-of-need-sqs.endpoint.url}") serviceEndpoint: String,
    @Value("\${complexity-of-need-sqs.endpoint.region}") region: String
  ): AmazonSQS =
    AmazonSQSClientBuilder.standard()
      .withEndpointConfiguration(AwsClientBuilder.EndpointConfiguration(serviceEndpoint, region))
      .withCredentials(AWSStaticCredentialsProvider(AnonymousAWSCredentials()))
      .build()

  @Bean("awsSqsDlqClientForComplexityOfNeed")
  @ConditionalOnProperty(name = ["complexity-of-need-sqs.provider"], havingValue = "localstack")
  fun awsSqsDlqClientForOffenderEventsLocalstack(
    @Value("\${complexity-of-need-sqs.endpoint.url}") serviceEndpoint: String,
    @Value("\${complexity-of-need-sqs.endpoint.region}") region: String
  ): AmazonSQS =
    AmazonSQSClientBuilder.standard()
      .withEndpointConfiguration(AwsClientBuilder.EndpointConfiguration(serviceEndpoint, region))
      .withCredentials(AWSStaticCredentialsProvider(AnonymousAWSCredentials()))
      .build()

  @Bean("queueUrlForComplexityOfNeed")
  @ConditionalOnProperty(name = ["complexity-of-need-sqs.provider"], havingValue = "localstack")
  @Suppress("SpringJavaInjectionPointsAutowiringInspection")
  fun queueUrl(
    @Autowired awsSqsClientForComplexityOfNeed: AmazonSQS,
    @Value("\${complexity-of-need-sqs.queue.name}") queueName: String,
    @Value("\${complexity-of-need-sqs.dlq.name}") dlqName: String
  ): String {
    val result = awsSqsClientForComplexityOfNeed.createQueue(CreateQueueRequest(dlqName))
    val dlqArn = awsSqsClientForComplexityOfNeed.getQueueAttributes(result.queueUrl, listOf(QueueAttributeName.QueueArn.toString()))
    awsSqsClientForComplexityOfNeed.createQueue(
      CreateQueueRequest(queueName).withAttributes(
        mapOf(
          QueueAttributeName.RedrivePolicy.toString() to
            """{"deadLetterTargetArn":"${dlqArn.attributes["QueueArn"]}","maxReceiveCount":"5"}"""
        )
      )
    )
    return awsSqsClientForComplexityOfNeed.getQueueUrl(queueName).queueUrl
  }
}
