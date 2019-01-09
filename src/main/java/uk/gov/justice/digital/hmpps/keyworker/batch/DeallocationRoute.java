package uk.gov.justice.digital.hmpps.keyworker.batch;

import org.apache.camel.builder.RouteBuilder;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import uk.gov.justice.digital.hmpps.keyworker.services.KeyworkerBatchService;


/**
 * A Scheduled job that deallocates prisoners that have moved to another prison or have been released.
 */
@Component
@ConditionalOnProperty(name = "quartz.enabled")
public class DeallocationRoute extends RouteBuilder {
    public static final String DIRECT_DEALLOCATION = "direct:deallocation";
    private static final String QUARTZ_UPDATE_STATUS_URI = "quartz2://application/deallocation?cron=";

    @Value("${deallocation.job.cron}")
    private String cronExpression;

    private final KeyworkerBatchService service;

    @Autowired
    public DeallocationRoute(KeyworkerBatchService service) {
        this.service = service;
    }

    @Override
    public void configure() {

        if (StringUtils.isNotBlank(cronExpression)) {
            from(QUARTZ_UPDATE_STATUS_URI + cronExpression)
                    .to(DIRECT_DEALLOCATION);
        }

        from(DIRECT_DEALLOCATION)
                .bean(service, "executeDeallocation")
                .log("Deallocation route complete");

    }
}
