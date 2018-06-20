package uk.gov.justice.digital.hmpps.keyworker.batch;


import org.quartz.CronTrigger;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SimpleTrigger;
import org.quartz.spi.JobFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.PropertiesFactoryBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.quartz.CronTriggerFactoryBean;
import org.springframework.scheduling.quartz.JobDetailFactoryBean;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;
import uk.gov.justice.digital.hmpps.keyworker.batch.DeallocateQuartzJob;
import uk.gov.justice.digital.hmpps.keyworker.batch.UpdateStatusQuartzJob;
import uk.gov.justice.digital.hmpps.keyworker.services.AutowiringSpringBeanJobFactory;

import javax.sql.DataSource;
import java.io.IOException;
import java.util.Properties;

@Configuration
@ConditionalOnProperty(name = "quartz.enabled")
public class SchedulerConfiguration {

    @Bean
    public JobFactory jobFactory(ApplicationContext applicationContext)
    {
        AutowiringSpringBeanJobFactory jobFactory = new AutowiringSpringBeanJobFactory();
        jobFactory.setApplicationContext(applicationContext);
        return jobFactory;
    }

    @Bean
    public Scheduler schedulerFactoryBean(DataSource dataSource, JobFactory jobFactory,
                                          @Qualifier("deallocationJobCron") CronTrigger deallocationJobCron,
                                          @Qualifier("updateStatusJobCron") CronTrigger updateStatusJobCron) throws Exception {
        SchedulerFactoryBean factory = new SchedulerFactoryBean();
        // this allows to update triggers in DB when updating settings in config file:
        factory.setOverwriteExistingJobs(true);
        factory.setDataSource(dataSource);
        factory.setJobFactory(jobFactory);

        factory.setQuartzProperties(quartzProperties());
        factory.afterPropertiesSet();

        Scheduler scheduler = factory.getScheduler();
        scheduler.setJobFactory(jobFactory);

        JobDetail deallocationJobDetail = (JobDetail) deallocationJobCron.getJobDataMap().get("jobDetail");
        if (scheduler.getJobDetail(deallocationJobDetail.getKey()) == null) {
            scheduler.scheduleJob(deallocationJobDetail, deallocationJobCron);
        }

        JobDetail updateStatusJobDetail = (JobDetail) updateStatusJobCron.getJobDataMap().get("jobDetail");
        if (scheduler.getJobDetail(updateStatusJobDetail.getKey()) == null) {
            scheduler.scheduleJob(updateStatusJobDetail, updateStatusJobCron);
        }



        scheduler.start();
        return scheduler;
    }

    @Bean
    public Properties quartzProperties() throws IOException {
        PropertiesFactoryBean propertiesFactoryBean = new PropertiesFactoryBean();
        propertiesFactoryBean.setLocation(new ClassPathResource("/quartz.properties"));
        propertiesFactoryBean.afterPropertiesSet();
        return propertiesFactoryBean.getObject();
    }

    @Bean
    public JobDetailFactoryBean deallocationJobDetail() {
        return createJobDetail(DeallocateQuartzJob.class);
    }

    @Bean
    public JobDetailFactoryBean updateStatusJobDetail() {
        return createJobDetail(UpdateStatusQuartzJob.class);
    }

    @Bean
    public CronTriggerFactoryBean deallocationJobCron(@Qualifier("deallocationJobDetail") JobDetail jobDetail,
                                                      @Value("${deallocation.job.cron}") String cronExpression) {
        return getCronTriggerFactoryBean(jobDetail, cronExpression);
    }

    @Bean
    public CronTriggerFactoryBean updateStatusJobCron(@Qualifier("updateStatusJobDetail") JobDetail jobDetail,
                                                      @Value("${updateStatus.job.cron}") String cronExpression) {
        return getCronTriggerFactoryBean(jobDetail, cronExpression);
    }

    private CronTriggerFactoryBean getCronTriggerFactoryBean(JobDetail jobDetail, String cronExpression) {
        CronTriggerFactoryBean factoryBean = new CronTriggerFactoryBean();
        factoryBean.setJobDetail(jobDetail);
        factoryBean.setCronExpression(cronExpression);
        factoryBean.setMisfireInstruction(SimpleTrigger.MISFIRE_INSTRUCTION_FIRE_NOW);
        return factoryBean;
    }

    private static JobDetailFactoryBean createJobDetail(Class jobClass) {
        JobDetailFactoryBean factoryBean = new JobDetailFactoryBean();
        factoryBean.setJobClass(jobClass);
        // job has to be durable to be stored in DB:
        factoryBean.setDurability(true);
        return factoryBean;
    }
}