package uk.gov.justice.digital.hmpps.keyworker.batch;

import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import uk.gov.justice.digital.hmpps.keyworker.services.KeyworkerBatchService;
import uk.gov.justice.digital.hmpps.keyworker.services.KeyworkerService;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Not a bean, and is wired manually by AutowiringSpringBeanJobFactory
 */
@Slf4j
public class UpdateStatusQuartzJob implements Job {

    @Autowired
    private KeyworkerBatchService keyworkerBatchService;

    @Override
    public void execute(JobExecutionContext jobExecutionContext) {
        try {
            keyworkerBatchService.executeUpdateStatus();

        } catch (Exception e) {
            log.error("Batch exception", e);
        }
    }
}