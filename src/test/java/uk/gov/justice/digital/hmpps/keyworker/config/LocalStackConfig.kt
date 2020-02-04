package uk.gov.justice.digital.hmpps.keyworker.config

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.testcontainers.containers.localstack.LocalStackContainer

@Configuration
@ConditionalOnExpression("'\${sqs.provider}'.equals('localstack') and '\${sqs.embedded}'.equals('true')")
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

}
