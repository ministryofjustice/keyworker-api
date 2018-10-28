package uk.gov.justice.digital.hmpps.keyworker.batch;

import org.apache.camel.builder.RouteBuilder;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import uk.gov.justice.digital.hmpps.keyworker.services.KeyworkerStatsService;
import uk.gov.justice.digital.hmpps.keyworker.services.PrisonSupportedService;


/**
 * A Scheduled job that checks builds stats for each prison for the previous day
 */
@Component
@ConditionalOnProperty(name = "quartz.enabled")
public class PrisonStatsRoute extends RouteBuilder {
    static final String DIRECT_PRISON_STATS = "direct:prisonStats";
    private static final String QUARTZ_PRISON_STATS_URI = "quartz2://application/prisonStats?cron=";

    @Value("${prisonStats.job.cron}")
    private String cronExpression;

    private final KeyworkerStatsService keyworkerStatsService;
    private final PrisonSupportedService prisonSupportedService;

    @Autowired
    public PrisonStatsRoute(KeyworkerStatsService keyworkerStatsService, PrisonSupportedService prisonSupportedService) {
        this.keyworkerStatsService = keyworkerStatsService;
        this.prisonSupportedService = prisonSupportedService;
    }

    @Override
    public void configure() {

        if (StringUtils.isNotBlank(cronExpression)) {
            from(QUARTZ_PRISON_STATS_URI + cronExpression)
                    .to(DIRECT_PRISON_STATS);
        }

        from(DIRECT_PRISON_STATS)
                .log("Starting: Daily Prison Statistics")
                .bean(prisonSupportedService, "getMigratedPrisons")
                .log("There are ${body.size} migrated prisons")
                .split(body())
                    .log("Gathering stats for ${body.prisonId}")
                    .bean(keyworkerStatsService, "generatePrisonStats(${body.prisonId})")
                    .log("Stats completed for ${body.prisonId}")
                .end()
                .log("Complete: Daily Prison Statistics");

    }
}
