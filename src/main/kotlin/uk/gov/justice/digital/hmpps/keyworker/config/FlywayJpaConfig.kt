package uk.gov.justice.digital.hmpps.keyworker.config

import org.flywaydb.core.Flyway
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.beans.factory.config.BeanFactoryPostProcessor
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import javax.sql.DataSource

/**
 * Explicit Flyway configuration to ensure migrations run before JPA.
 * This fixes initialization order issues in Spring Boot 4 / Hibernate 7.
 */
@Configuration
class FlywayJpaConfig {
  private val log = LoggerFactory.getLogger(this::class.java)

  @Bean
  fun flyway(
    dataSource: DataSource,
    @Value("\${spring.flyway.locations:classpath:db/migration}") locations: String,
    @Value("\${spring.flyway.baseline-on-migrate:true}") baselineOnMigrate: Boolean,
    @Value("\${spring.flyway.validate-on-migrate:false}") validateOnMigrate: Boolean,
  ): Flyway {
    log.info("Configuring Flyway with locations: {}", locations)
    val flyway =
      Flyway
        .configure()
        .dataSource(dataSource)
        .locations(locations)
        .baselineOnMigrate(baselineOnMigrate)
        .validateOnMigrate(validateOnMigrate)
        .load()

    log.info("Running Flyway migrations...")
    val result = flyway.migrate()
    log.info("Flyway: {} migrations executed", result.migrationsExecuted)

    return flyway
  }

  companion object {
    @JvmStatic
    @Bean
    fun flywayInitializerPostProcessor(): BeanFactoryPostProcessor =
      BeanFactoryPostProcessor { beanFactory: ConfigurableListableBeanFactory ->
        if (beanFactory.containsBeanDefinition("entityManagerFactory")) {
          val emfDefinition = beanFactory.getBeanDefinition("entityManagerFactory")
          val existingDeps = emfDefinition.dependsOn ?: emptyArray()
          if (!existingDeps.contains("flyway")) {
            emfDefinition.setDependsOn(*existingDeps, "flyway")
          }
        }
      }
  }
}
