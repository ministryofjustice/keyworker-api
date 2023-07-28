package uk.gov.justice.digital.hmpps.keyworker.batch

import org.quartz.CronScheduleBuilder
import org.quartz.JobBuilder
import org.quartz.JobDetail
import org.quartz.Trigger
import org.quartz.TriggerBuilder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.quartz.SchedulerFactoryBean
import javax.sql.DataSource

/* requires postgres db - does not work with in memory database */
@Configuration
@ConditionalOnProperty(name = ["quartz.enabled"])
internal class QuartzConfigurationFactory {

  //@Value("\${enable-new-nomis.job.cron}")
  private val enableNewNomisJobCron: String = "0 0/1 * * * ?"

  @Autowired
  private lateinit var dataSource: DataSource

  @Bean
  fun schedulerFactoryBean(): SchedulerFactoryBean {
    val factory = SchedulerFactoryBean()
    factory.setDataSource(dataSource)
    return factory
  }

  @Bean
  fun enableNewNomisJobDetail(): JobDetail {
    return JobBuilder.newJob(EnableNewNomisJob::class.java).storeDurably()
      .withIdentity(EnableNewNomisJob.ENABLE_NEW_NOMIS).build()
  }

  @Bean
  fun enableNewNomisJobTrigger(): Trigger {
    return TriggerBuilder.newTrigger().withIdentity(EnableNewNomisJob.ENABLE_NEW_NOMIS).forJob(enableNewNomisJobDetail())
      .withSchedule(CronScheduleBuilder.cronSchedule(enableNewNomisJobCron)).build()
  }
}
