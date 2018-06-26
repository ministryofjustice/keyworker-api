package uk.gov.justice.digital.hmpps.keyworker.batch;

import org.apache.camel.builder.RouteBuilder;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import uk.gov.justice.digital.hmpps.keyworker.services.KeyworkerBatchService;


/**
 * A Scheduled job that checks for any key workers with reached active dates. KEYWORKER.ACTIVE_DATE
 * and updates their current status to active
 */
@Component
@ConditionalOnProperty(name = "quartz.enabled")
public class UpdateStatusRoute extends RouteBuilder {
    static final String DIRECT_UPDATE_STATUS = "direct:updateStatus";
    private static final String QUARTZ_UPDATE_STATUS_URI = "quartz2://application/updateStatus?cron=";

    @Value("${updateStatus.job.cron}")
    private String cronExpression;

    private final KeyworkerBatchService service;

    @Autowired
    public UpdateStatusRoute(KeyworkerBatchService service) {
        this.service = service;
    }

    @Override
    public void configure() {

        if (StringUtils.isNotBlank(cronExpression)) {
            from(QUARTZ_UPDATE_STATUS_URI + cronExpression)
                    .to(DIRECT_UPDATE_STATUS);
        }

        from(DIRECT_UPDATE_STATUS)
                .bean(service, "executeUpdateStatus")
                .log("Keyworkers updated to active status: ${body.size}");

    }
}
