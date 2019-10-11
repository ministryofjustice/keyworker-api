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
        response.body.contains("{\"status\":\"DOWN\",\"details\":{\"elite2ApiHealth\":{\"status\":\"DOWN\",\"details\":{\"error\":\"org.springframework.web.client.ResourceAccessException: I/O error on GET request for \\\"http://localhost:8999/ping\\\": Read timed out; nested exception is java.net.SocketTimeoutException: Read timed out\"}}")
    }
}
