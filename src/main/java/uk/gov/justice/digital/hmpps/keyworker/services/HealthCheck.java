package uk.gov.justice.digital.hmpps.keyworker.services;

import lombok.AllArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;

import static lombok.AccessLevel.PROTECTED;

@AllArgsConstructor(access = PROTECTED)
public abstract class HealthCheck implements HealthIndicator {
    private final WebClient webClient;
    private final Duration timeout;

    @Override
    public Health health() {
        try {
            final var responseEntity =
                    webClient.get()
                            .uri("/health/ping")
                            .retrieve()
                            .toEntity(String.class)
                            .block(timeout);
            return Health.up().withDetail("HttpStatus", responseEntity.getStatusCode()).build();
        } catch (final WebClientResponseException e) {
            return Health.down(e).withDetail("body", e.getResponseBodyAsString()).build();
        } catch (final Exception e) {
            return Health.down(e).build();
        }
    }
}
