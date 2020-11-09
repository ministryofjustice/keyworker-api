package uk.gov.justice.digital.hmpps.keyworker.batch

import org.apache.camel.CamelContext
import org.apache.camel.ProducerTemplate
import org.apache.camel.component.quartz2.QuartzComponent
import org.apache.camel.spring.boot.CamelContextConfiguration
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.*

/* requires postgres db - does not work with in memory database */
@Configuration
@ConditionalOnProperty(name = ["quartz.enabled"])
internal class CamelQuartzConfigurationFactory(
    @Autowired
    private val camelContext: CamelContext,

    @Value("\${org.quartz.jobStore.class}")
    private val quartzJobstoreClass: String,

    @Value("\${org.quartz.threadPool.threadCount}")
    private val threadCount: String,

    @Value("\${app.db.url}")
    private val url: String,

    @Value("\${spring.datasource.username}")
    private val username: String,

    @Value("\${spring.datasource.password}")
    private val password: String,

    @Value("\${org.quartz.jobStore.driverDelegateClass}")
    private val quartzDelegateClass: String,

    @Value("\${database.driver.class}")
    private val databaseDriverClass: String,

    @Value("\${org.quartz.jobStore.misfireThreshold}")
    private val misfireThreshold: String,

    @Value("\${org.quartz.jobStore.tablePrefix}")
    private val tablePrefix: String,

    @Value("\${org.quartz.jobStore.clusterCheckinInterval}")
    private val clusterCheckinInterval: String,

    @Value("\${org.quartz.jobStore.isClustered}")
    private val isClustered: String,

    @Value("\${org.quartz.scheduler.instanceName}")
    private val instanceName: String,

    @Value("\${org.quartz.scheduler.instanceId}")
    private val instanceId: String
) {

    @Bean
    fun contextConfiguration(): CamelContextConfiguration {
        return object : CamelContextConfiguration {
            override fun beforeApplicationStart(context: CamelContext) {
                context.addComponent("quartz2", quartz())
                context.isUseMDCLogging = true
            }

            override fun afterApplicationStart(camelContext: CamelContext) {
                // no changes after started required.
            }
        }
    }

    @Bean
    fun producerTemplate(): ProducerTemplate {
        return camelContext.createProducerTemplate()
    }

    @Bean
    fun quartz(): QuartzComponent {
        val quartz = QuartzComponent()
        val properties = Properties()
        properties["org.quartz.jobStore.dataSource"] = "applicationDS"
        properties["org.quartz.jobStore.driverDelegateClass"] = quartzDelegateClass
        properties["org.quartz.dataSource.applicationDS.driver"] = databaseDriverClass
        properties["org.quartz.dataSource.applicationDS.URL"] = url
        properties["org.quartz.dataSource.applicationDS.user"] = username
        properties["org.quartz.dataSource.applicationDS.password"] = password
        properties["org.quartz.jobStore.class"] = quartzJobstoreClass
        properties["quartz.scheduler.instanceName"] = instanceName
        properties["org.quartz.scheduler.instanceId"] = instanceId
        properties["org.quartz.threadPool.class"] = "org.quartz.simpl.SimpleThreadPool"
        properties["org.quartz.threadPool.threadCount"] = threadCount
        properties["org.quartz.threadPool.threadPriority"] = "5"
        properties["org.quartz.jobStore.misfireThreshold"] = misfireThreshold
        properties["org.quartz.jobStore.useProperties"] = "false"
        properties["org.quartz.jobStore.tablePrefix"] = tablePrefix
        properties["org.quartz.jobStore.isClustered"] = isClustered
        properties["org.quartz.jobStore.clusterCheckinInterval"] = clusterCheckinInterval
        quartz.properties = properties
        return quartz
    }
}
