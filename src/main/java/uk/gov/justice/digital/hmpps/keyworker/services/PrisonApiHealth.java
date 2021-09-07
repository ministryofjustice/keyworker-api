package uk.gov.justice.digital.hmpps.keyworker.services;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

@Component
public class PrisonApiHealth extends HealthCheck {

    public PrisonApiHealth(@Qualifier("healthWebClient") final WebClient healthWebClient,
                           @Value("${api.health-timeout-ms}") final Duration healthTimeout) {
        super(healthWebClient, healthTimeout);
    }
}
