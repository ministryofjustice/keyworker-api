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
        def response = restTemplate.exchange("/health", HttpMethod.GET, createHeaderEntity("headers"), String.class)

        then:
        response.statusCode == HttpStatus.OK
        def details = jsonSlurper.parseText(response.body)

        details.status == "UP"
    }
}
