package uk.gov.justice.digital.hmpps.keyworker.services;

import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Not a bean, and is wired manually by AutowiringSpringBeanJobFactory
 */
@Slf4j
public class DeallocateQuartzJob implements Job {

    @Autowired
    private DeallocateJob deallocateJob;

    @Value("${api.keyworker.initial.deallocate.threshold}")
    private String initialDeallocateThreshold;

    @Override
    public void execute(JobExecutionContext jobExecutionContext) {
        try {
            deallocateJob.execute(getPreviousJobStart(jobExecutionContext));

        } catch (Exception e) {
            log.error("Batch exception", e);
        }
    }

    private LocalDateTime getPreviousJobStart(JobExecutionContext jobExecutionContext) {
        if (jobExecutionContext.getPreviousFireTime() == null) {
            return LocalDateTime.parse(initialDeallocateThreshold);
        }
        return LocalDateTime.ofInstant(jobExecutionContext.getPreviousFireTime().toInstant(), ZoneOffset.UTC);
    }
}