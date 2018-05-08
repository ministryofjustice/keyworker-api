package uk.gov.justice.digital.hmpps.keyworker.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
public class Elite2ApiHealth implements HealthIndicator {

    private final RestTemplate restTemplate;

    @Autowired
    public Elite2ApiHealth(@Qualifier("elite2ApiHealthRestTemplate") RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public Health health() {
        try {
            final ResponseEntity<String> responseEntity = this.restTemplate.getForEntity("/health", String.class);
            return health(responseEntity.getStatusCode());
        } catch (RestClientException e) {
            return health(HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    private Health health(HttpStatus code) {
        return health (
                code.is2xxSuccessful() ? Health.up(): Health.down(),
                code);
    }

    private Health health(Health.Builder builder, HttpStatus code) {
        return builder
                .withDetail("HttpStatus", code.value())
                .build();
    }
}
