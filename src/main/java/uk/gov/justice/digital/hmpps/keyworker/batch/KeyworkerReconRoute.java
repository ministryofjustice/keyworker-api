package uk.gov.justice.digital.hmpps.keyworker.batch;

import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import uk.gov.justice.digital.hmpps.keyworker.services.PrisonSupportedService;
import uk.gov.justice.digital.hmpps.keyworker.services.ReconciliationService;


/**
 * A Scheduled job that checks builds stats for each prison for the previous day
 */
@Component
@ConditionalOnProperty(name = "quartz.enabled")
public class KeyworkerReconRoute extends RouteBuilder {
    public static final String DIRECT_KEY_WORKER_RECON = "direct:keyWorkerRecon";
    private static final String QUARTZ_KEY_WORKER_RECON_URI = "quartz2://application/keyWorkerRecon?cron=";
    private static final String DIRECT_RECON = "direct:recon";
    private static final String DIRECT_LOG_ERROR = "direct:recon-log-error";

    @Value("${key.worker.recon.job.cron}")
    private String cronExpression;

    private final ReconciliationService reconciliationService;
    private final PrisonSupportedService prisonSupportedService;

    @Autowired
    public KeyworkerReconRoute(final ReconciliationService reconciliationService, final PrisonSupportedService prisonSupportedService) {
        this.reconciliationService = reconciliationService;
        this.prisonSupportedService = prisonSupportedService;
    }

    @Override
    public void configure() {
        getContext().setStreamCaching(true);

        if (StringUtils.isNotBlank(cronExpression)) {
            from(QUARTZ_KEY_WORKER_RECON_URI + cronExpression)
                    .to(DIRECT_KEY_WORKER_RECON);
        }

        from(DIRECT_KEY_WORKER_RECON)
                .log("Starting: Key Worker Reconciliation")
                .bean(prisonSupportedService, "getMigratedPrisons")
                .log("There are ${body.size} prisons")
                .split(body())
                    .to(DIRECT_RECON)
                .end()
                .log("Complete: Key Worker Reconciliation");

        from(DIRECT_RECON)
                .errorHandler(deadLetterChannel(DIRECT_LOG_ERROR).redeliveryDelay(3000).backOffMultiplier(1.37).maximumRedeliveries(2))
                .log("Key Worker Reconciliation for ${body.prisonId}")
                .bean(reconciliationService, "reconcileKeyWorkerAllocations(${body.prisonId})")
                .log("Stats completed for ${body.prisonId}");

        from(DIRECT_LOG_ERROR)
                .log(LoggingLevel.ERROR, "Error occurred processing ${body.prisonId}")
                .to("log:recon-error?level=ERROR&showCaughtException=true&showStackTrace=true&showAll=true")
                .bean(reconciliationService , "raiseStatsProcessingError(${body.prisonId})");

    }
}
