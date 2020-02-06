package uk.gov.justice.digital.hmpps.keyworker.config

import com.amazonaws.services.sqs.AmazonSQS
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.testcontainers.containers.localstack.LocalStackContainer

@Configuration
@ConditionalOnProperty(name = ["sqs.provider"], havingValue = "embedded-localstack")
open class LocalStackConfig {
  companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }

  @Bean
  open fun localStackContainer(): LocalStackContainer {
    log.info("Starting embedded localstack...")
    val localStackContainer: LocalStackContainer = LocalStackContainer()
        .withServices(LocalStackContainer.Service.SQS)
        .withEnv("HOSTNAME_EXTERNAL", "localhost")

    localStackContainer.start()
    log.info("Started embedded localstack.")
    return localStackContainer
  }

  @Bean
  @Suppress("SpringJavaInjectionPointsAutowiringInspection")
  open fun queueUrl(@Autowired awsSqsClient: AmazonSQS,
                    @Value("\${sqs.queue.name}") queueName: String,
                    @Value("\${sqs.dlq.name}") dlqName: String): String =
      JmsConfig().queueUrl(awsSqsClient, queueName, dlqName)
}
