package uk.gov.justice.digital.hmpps.keyworker.config;

import com.microsoft.applicationinsights.web.internal.RequestTelemetryContext;
import com.microsoft.applicationinsights.web.internal.ThreadContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.ConfigFileApplicationContextInitializer;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import uk.gov.justice.digital.hmpps.keyworker.utils.JwtAuthenticationHelper;
import uk.gov.justice.digital.hmpps.keyworker.utils.JwtAuthenticationHelper.JwtParameters;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@Import({JwtAuthenticationHelper.class, ClientTrackingTelemetryModule.class, PublicKeyClient.class})
@ContextConfiguration(initializers = {ConfigFileApplicationContextInitializer.class})
@ActiveProfiles("test")
class ClientTrackingTelemetryModuleTest {

    @Autowired
    private ClientTrackingTelemetryModule clientTrackingTelemetryModule;

    @Autowired
    private JwtAuthenticationHelper jwtAuthenticationHelper;

    @BeforeEach
    void setup() {
        ThreadContext.setRequestTelemetryContext(new RequestTelemetryContext(1L));
    }

    @AfterEach
    void tearDown() {
        ThreadContext.remove();
    }

    @Test
    void shouldAddClientIdAndUserNameToInsightTelemetry() {

        final var token = createJwt("bob", List.of(), 1L);

        final var req = new MockHttpServletRequest();
        req.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        final var res = new MockHttpServletResponse();

        clientTrackingTelemetryModule.onBeginRequest(req, res);

        final var insightTelemetry = ThreadContext.getRequestTelemetryContext().getHttpRequestTelemetry().getProperties();

        assertThat(insightTelemetry).hasSize(2);
        assertThat(insightTelemetry.get("username")).isEqualTo("bob");
        assertThat(insightTelemetry.get("clientId")).isEqualTo("keyworkerApiClient");

    }

    @Test
    void shouldAddOnlyClientIdIfUsernameNullToInsightTelemetry() {

        final var token = createJwt(null, List.of(), 1L);

        final var req = new MockHttpServletRequest();
        req.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        final var res = new MockHttpServletResponse();

        clientTrackingTelemetryModule.onBeginRequest(req, res);

        final var insightTelemetry = ThreadContext.getRequestTelemetryContext().getHttpRequestTelemetry().getProperties();

        assertThat(insightTelemetry).hasSize(1);
        assertThat(insightTelemetry.get("clientId")).isEqualTo("keyworkerApiClient");

    }

    @Test
    void shouldNotAddClientIdAndUserNameToInsightTelemetryAsTokenExpired() {

        final var token = createJwt("Fred", List.of(), -1L);

        final var req = new MockHttpServletRequest();
        req.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        final var res = new MockHttpServletResponse();

        clientTrackingTelemetryModule.onBeginRequest(req, res);

        final var insightTelemetry = ThreadContext.getRequestTelemetryContext().getHttpRequestTelemetry().getProperties();

        assertThat(insightTelemetry).isEmpty();
    }

    private String createJwt(final String user, final List<String> roles, final Long duration) {
        return jwtAuthenticationHelper.createJwt(JwtParameters.builder()
                .username(user)
                .roles(roles)
                .scope(List.of("read", "write"))
                .expiryTime(Duration.ofDays(duration))
                .build());
    }
}
