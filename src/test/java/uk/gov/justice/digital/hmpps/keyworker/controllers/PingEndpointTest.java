package uk.gov.justice.digital.hmpps.keyworker.controllers;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PingEndpointTest {
    @Test
    public void ping() {
        assertThat(new PingEndpoint().ping()).isEqualTo("pong");
    }
}
