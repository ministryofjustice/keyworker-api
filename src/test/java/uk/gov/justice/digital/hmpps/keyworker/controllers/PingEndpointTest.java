package uk.gov.justice.digital.hmpps.keyworker.controllers;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PingEndpointTest {
    @Test
    void ping() {
        assertThat(new PingEndpoint().ping()).isEqualTo("pong");
    }
}
