package uk.gov.justice.digital.hmpps.keyworker.integration.specs

import com.github.tomakehurst.wiremock.client.WireMock
import groovy.json.JsonSlurper
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus

class HealthSpecification extends TestSpecification {

    def jsonSlurper = new JsonSlurper()


    def "Health page reports ok"() {
        WireMock.reset()

        given:
        elite2api.stubHealthOKResponse()

        when:
        def response = restTemplate.exchange("/ping", HttpMethod.GET, createHeaderEntity("headers"), String.class)

        then:
        response.statusCode == HttpStatus.OK
        response.body == 'pong'
    }

    def "Health page dependancy timeout"() {
        WireMock.reset()

        given:
        elite2api.stubHealthDependencyTimeoutResponse()

        when:
        def response = restTemplate.exchange("/health", HttpMethod.GET, createHeaderEntity("headers"), String.class)

        then:
        response.statusCode == HttpStatus.SERVICE_UNAVAILABLE
        response.body.contains("\"elite2ApiHealth\":{\"status\":\"DOWN\",\"details\":{\"error\":\"java.lang.IllegalStateException: Timeout on blocking read for 400 MILLISECONDS\"}}")
    }
}
