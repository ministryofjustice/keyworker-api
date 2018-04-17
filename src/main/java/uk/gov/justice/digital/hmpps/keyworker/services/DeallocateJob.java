package uk.gov.justice.digital.hmpps.keyworker.services;

import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class DeallocateJob implements Job {

    private final KeyworkerService service;

    public DeallocateJob(KeyworkerService service) {
        this.service = service;
    }

    @Override
    public void execute(JobExecutionContext jobExecutionContext) {
        // do something here
        log.info("De-allocation Process Called");
    }
}