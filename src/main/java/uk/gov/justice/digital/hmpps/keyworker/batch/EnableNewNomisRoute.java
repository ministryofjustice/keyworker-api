package uk.gov.justice.digital.hmpps.keyworker.batch;

import com.google.common.collect.ImmutableMap;
import com.microsoft.applicationinsights.TelemetryClient;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import uk.gov.justice.digital.hmpps.keyworker.services.NomisService;


/**
 * A Scheduled job that checks builds stats for each prison for the previous day
 */
@Component
@ConditionalOnProperty(name = "quartz.enabled")
public class EnableNewNomisRoute extends RouteBuilder {
    static final String ENABLE_NEW_NOMIS = "direct:enableNewNomis";
    private static final String QUARTZ_ENABLE_NEW_NOMIS_URI = "quartz2://application/enableNewNomis?cron=";
    private static final String ADD_NWEB_CASELOAD = "direct:add-nweb-caseload";
    private static final String DIRECT_LOG_ERROR = "direct:log-error";

    @Value("${enable-new-nomis.job.cron}")
    private String cronExpression;

    private final NomisService nomisService;
    private final TelemetryClient telemetryClient;

    @Autowired
    public EnableNewNomisRoute(NomisService nomisService, TelemetryClient telemetryClient) {
        this.nomisService = nomisService;
        this.telemetryClient = telemetryClient;
    }

    @Override
    public void configure() {

        if (StringUtils.isNotBlank(cronExpression)) {
            from(QUARTZ_ENABLE_NEW_NOMIS_URI + cronExpression)
                    .to(ENABLE_NEW_NOMIS);
        }

        from(ENABLE_NEW_NOMIS)
                .log("Starting: Checking for new Users and Enabling in New Nomis")
                .bean(nomisService, "getAllPrisons")
                .log("There are ${body.size} prisons")
                .split(body())
                    .to(ADD_NWEB_CASELOAD)
                .end()
                .log("Complete: Checking for new Users and Enabling in New Nomis");

        from(ADD_NWEB_CASELOAD)
                .errorHandler(deadLetterChannel(DIRECT_LOG_ERROR).redeliveryDelay(5000).backOffMultiplier(0.29).maximumRedeliveries(2))
                .setProperty("prisonId", simple("${body.prisonId}"))
                .log("Enabling New Nomis for all active used in prison ${body.prisonId}")
                .bean(nomisService, "enableNewNomisForCaseload(${body.prisonId})")
                .log("Enabled ${body} new users")
                .process(p -> {
                    var infoMap = ImmutableMap.of("prisonId", exchangeProperty("prisonId").toString(),
                            "numNewUsers", String.valueOf(p.getIn().getBody(Integer.class)));
                    telemetryClient.trackEvent("NewNomisUserEnabled", infoMap,null);
                })
        ;

        from(DIRECT_LOG_ERROR)
                .log(LoggingLevel.ERROR, "Error occurred processing ${body.prisonId}")
                .to("log:stats-error?level=ERROR&showCaughtException=true&showStackTrace=true&showAll=true");

    }
}
