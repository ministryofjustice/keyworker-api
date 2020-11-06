package uk.gov.justice.digital.hmpps.keyworker.config

import com.microsoft.applicationinsights.web.internal.RequestTelemetryContext
import com.microsoft.applicationinsights.web.internal.ThreadContext
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.ConfigFileApplicationContextInitializer
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit.jupiter.SpringExtension
import uk.gov.justice.digital.hmpps.keyworker.utils.JwtAuthenticationHelper
import uk.gov.justice.digital.hmpps.keyworker.utils.JwtAuthenticationHelper.JwtParameters
import java.time.Duration

@ExtendWith(SpringExtension::class)
@Import(JwtAuthenticationHelper::class, ClientTrackingTelemetryModule::class)
@ContextConfiguration(initializers = [ConfigFileApplicationContextInitializer::class])
@ActiveProfiles("test")
internal class ClientTrackingTelemetryModuleTest {

    @Autowired
    private lateinit var clientTrackingTelemetryModule: ClientTrackingTelemetryModule

    @Autowired
    private lateinit var jwtAuthenticationHelper: JwtAuthenticationHelper

    @BeforeEach
    fun setup() {
        ThreadContext.setRequestTelemetryContext(RequestTelemetryContext(1L))
    }

    @AfterEach
    fun tearDown() {
        ThreadContext.remove()
    }

    @Test
    fun shouldAddClientIdAndUserNameToInsightTelemetry() {
        val token = createJwt("bob", java.util.List.of(), 1L)
        val req = MockHttpServletRequest()
        req.addHeader(HttpHeaders.AUTHORIZATION, "Bearer $token")
        val res = MockHttpServletResponse()
        clientTrackingTelemetryModule.onBeginRequest(req, res)
        val insightTelemetry = ThreadContext.getRequestTelemetryContext().httpRequestTelemetry.properties
        Assertions.assertThat(insightTelemetry).containsOnly(Assertions.entry("username", "bob"), Assertions.entry("clientId", "keyworkerApiClient"))
    }

    @Test
    fun shouldAddOnlyClientIdIfUsernameNullToInsightTelemetry() {
        val token = createJwt(null, java.util.List.of(), 1L)
        val req = MockHttpServletRequest()
        req.addHeader(HttpHeaders.AUTHORIZATION, "Bearer $token")
        val res = MockHttpServletResponse()
        clientTrackingTelemetryModule.onBeginRequest(req, res)
        val insightTelemetry = ThreadContext.getRequestTelemetryContext().httpRequestTelemetry.properties
        Assertions.assertThat(insightTelemetry).containsOnly(Assertions.entry("clientId", "keyworkerApiClient"))
    }

    @Test
    fun shouldAddClientIdAndUserNameToInsightTelemetryEvenIfTokenExpired() {
        val token = createJwt("Fred", java.util.List.of(), -1L)
        val req = MockHttpServletRequest()
        req.addHeader(HttpHeaders.AUTHORIZATION, "Bearer $token")
        val res = MockHttpServletResponse()
        clientTrackingTelemetryModule.onBeginRequest(req, res)
        val insightTelemetry = ThreadContext.getRequestTelemetryContext().httpRequestTelemetry.properties
        Assertions.assertThat(insightTelemetry).containsOnly(Assertions.entry("username", "Fred"), Assertions.entry("clientId", "keyworkerApiClient"))
    }

    private fun createJwt(user: String?, roles: List<String>, duration: Long): String {
        return jwtAuthenticationHelper.createJwt(JwtParameters(
                username = user,
                roles = roles,
                scope = java.util.List.of("read", "write"),
                expiryTime = Duration.ofDays(duration))
        )
    }
}