package uk.gov.justice.digital.hmpps.keyworker.batch;

import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import uk.gov.justice.digital.hmpps.keyworker.services.KeyworkerBatchService;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Not a bean, and is wired manually by AutowiringSpringBeanJobFactory
 */
@Slf4j
public class DeallocateQuartzJob implements Job {

    @Autowired
    private KeyworkerBatchService keyworkerBatchService;

    @Value("${api.keyworker.initial.deallocate.threshold}")
    private String initialDeallocateThreshold;

    @Override
    public void execute(JobExecutionContext jobExecutionContext) {
        keyworkerBatchService.executeDeallocation(getPreviousJobStart(jobExecutionContext));
    }

    private LocalDateTime getPreviousJobStart(JobExecutionContext jobExecutionContext) {
        if (jobExecutionContext.getPreviousFireTime() == null) {
            return LocalDateTime.parse(initialDeallocateThreshold);
        }
        return LocalDateTime.ofInstant(jobExecutionContext.getPreviousFireTime().toInstant(), ZoneOffset.UTC);
    }
}