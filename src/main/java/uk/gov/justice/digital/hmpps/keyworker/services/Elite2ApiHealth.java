package uk.gov.justice.digital.hmpps.keyworker.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
public class Elite2ApiHealth implements HealthIndicator {

    private final RestTemplate restTemplate;

    @Autowired
    public Elite2ApiHealth(@Qualifier("elite2ApiHealthRestTemplate") final RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public Health health() {
        try {
            final var responseEntity = this.restTemplate.getForEntity("/ping", String.class);
            return health(responseEntity.getStatusCode());
        } catch (final RestClientException e) {
            return health(HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    private Health health(final HttpStatus code) {
        return health (
                Health.up(),
                code);
    }

    private Health health(final Health.Builder builder, final HttpStatus code) {
        return builder
                .withDetail("HttpStatus", code.value())
                .build();
    }
}
