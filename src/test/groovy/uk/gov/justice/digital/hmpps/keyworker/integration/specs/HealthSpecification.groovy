package uk.gov.justice.digital.hmpps.keyworker.integration.specs

import groovy.json.JsonSlurper
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus

class HealthSpecification extends TestSpecification {

    def jsonSlurper = new JsonSlurper()

    def "Health page reports ok"() {

        when:
        def response = restTemplate.exchange("/health", HttpMethod.GET, createHeaderEntity("headers"), String.class)

        then:
        response.statusCode == HttpStatus.OK
        def details = jsonSlurper.parseText(response.body)

        details.healthInfo.status == "UP"
        // details.healthInfo.version == "version not available"
        details.status == "UP"
        details.db.status == "UP"
    }
}
