package uk.gov.justice.digital.hmpps.keyworker.integration.specs

import com.github.tomakehurst.wiremock.admin.model.ListStubMappingsResult
import com.github.tomakehurst.wiremock.client.WireMock
import groovy.json.JsonSlurper
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus

class HealthSpecification extends TestSpecification {

    def jsonSlurper = new JsonSlurper()

    def "Health page reports ok if elite2api returns error status"() {
        WireMock.reset()
        given:
        elite2api.stubHealthElite2DownResponse()

        when:
        def response = restTemplate.exchange("/health", HttpMethod.GET, createHeaderEntity("headers"), String.class)

        then:
        response.statusCode == HttpStatus.OK
        def details = jsonSlurper.parseText(response.body)

        details.healthInfo.status == "UP"
        details.elite2ApiHealth.status == "UP"
        details.elite2ApiHealth.HttpStatus == 503
        details.status == "UP"
        details.db.status == "UP"
    }

    def "Health page reports ok"() {
        WireMock.reset()

        given:
        elite2api.stubHealthOKResponse()

        when:
        def response = restTemplate.exchange("/health", HttpMethod.GET, createHeaderEntity("headers"), String.class)

        then:
        response.statusCode == HttpStatus.OK
        def details = jsonSlurper.parseText(response.body)

        details.healthInfo.status == "UP"
        details.elite2ApiHealth.status == "UP"
        details.status == "UP"
        details.db.status == "UP"
    }
}
