package uk.gov.justice.digital.hmpps.keyworker.batch;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.quartz2.QuartzComponent;
import org.apache.camel.spring.boot.CamelContextConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Properties;

/* requires postgres db - does not work with in memory database */
@Configuration
@ConditionalOnProperty(name = "quartz.enabled")
class CamelQuartzConfigurationFactory {

    @Autowired
    private CamelContext camelContext;

    @Value("${org.quartz.jobStore.class}")
    private String quartzJobstoreClass;

    @Value("${org.quartz.threadPool.threadCount}")
    private String threadCount;

    @Value("${app.db.url}")
    private String url;

    @Value("${spring.datasource.username}")
    private String username;

    @Value("${spring.datasource.password}")
    private String password;

    @Value("${org.quartz.jobStore.driverDelegateClass}")
    private String quartzDelegateClass;

    @Value("${database.driver.class}")
    private String databaseDriverClass;

    @Value("${org.quartz.jobStore.misfireThreshold}")
    private String misfireThreshold;

    @Value("${org.quartz.jobStore.tablePrefix}")
    private String tablePrefix;

    @Value("${org.quartz.jobStore.clusterCheckinInterval}")
    private String clusterCheckinInterval;

    @Value("${org.quartz.jobStore.isClustered}")
    private String isClustered;

    @Value("${org.quartz.scheduler.instanceName}")
    private String instanceName;

    @Value("${org.quartz.scheduler.instanceId}")
    private String instanceId;


    @Bean
    public CamelContextConfiguration contextConfiguration() {
        return new CamelContextConfiguration() {
            @Override
            public void beforeApplicationStart(CamelContext context) {
                context.addComponent("quartz2", quartz());
                context.setUseMDCLogging(true);
            }

            @Override
            public void afterApplicationStart(CamelContext camelContext) {
                // no changes after started required.
            }
        };
    }

    @Bean
    public ProducerTemplate producerTemplate() {
        return camelContext.createProducerTemplate();
    }

    @Bean
    public QuartzComponent quartz() {
        QuartzComponent quartz = new QuartzComponent();
        final Properties properties = new Properties();

        properties.put("org.quartz.jobStore.dataSource", "applicationDS");
        properties.put("org.quartz.jobStore.driverDelegateClass", quartzDelegateClass);
        properties.put("org.quartz.dataSource.applicationDS.driver", databaseDriverClass);
        properties.put("org.quartz.dataSource.applicationDS.URL", url);
        properties.put("org.quartz.dataSource.applicationDS.user", username);
        properties.put("org.quartz.dataSource.applicationDS.password", password);
        properties.put("org.quartz.jobStore.class", quartzJobstoreClass);

        properties.put("quartz.scheduler.instanceName", instanceName);
        properties.put("org.quartz.scheduler.instanceId", instanceId);
        properties.put("org.quartz.threadPool.class", "org.quartz.simpl.SimpleThreadPool");
        properties.put("org.quartz.threadPool.threadCount", threadCount);
        properties.put("org.quartz.threadPool.threadPriority", "5");
        properties.put("org.quartz.jobStore.misfireThreshold", misfireThreshold);

        properties.put("org.quartz.jobStore.useProperties", "false");
        properties.put("org.quartz.jobStore.tablePrefix", tablePrefix);
        properties.put("org.quartz.jobStore.isClustered", isClustered);
        properties.put("org.quartz.jobStore.clusterCheckinInterval", clusterCheckinInterval);

        quartz.setProperties(properties);

        return quartz;
    }
}
