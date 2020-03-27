package uk.gov.justice.digital.hmpps.keyworker.integration.specs

import com.github.tomakehurst.wiremock.client.WireMock
import org.springframework.http.HttpStatus

import static org.assertj.core.api.Assertions.assertThat

class HealthSpecification extends TestSpecification {


    def "Health page reports ok"() {
        WireMock.reset()

        given:
        elite2api.stubHealthOKResponse()

        when:
        getForEntity("/health/ping", createHeaderEntity())
            .expectStatus().is2xxSuccessful()
            .expectBody().is('{"status":"UP"}')

        then:
        noExceptionThrown()
    }

    def "Health page dependancy timeout"() {
        WireMock.reset()

        given:
        elite2api.stubHealthDependencyTimeoutResponse()

        when:
        getForEntity("/health", createHeaderEntity())
                .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                .expectBody()
                .jsonPath('$.components.elite2ApiHealth.status').isEqualTo("DOWN")
                .jsonPath('$.components.elite2ApiHealth.details.error').value { error -> assertThat(error).contains("Timeout") }

        then:
        noExceptionThrown()
    }
}
