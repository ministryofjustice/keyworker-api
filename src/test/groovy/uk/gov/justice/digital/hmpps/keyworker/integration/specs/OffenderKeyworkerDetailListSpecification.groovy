package uk.gov.justice.digital.hmpps.keyworker.integration.specs

import groovy.json.JsonSlurper
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus

class OffenderKeyworkerDetailListSpecification extends TestSpecification {

    def jsonSlurper = new JsonSlurper()

    def 'Retrieve offender keyworker details using POST endpoint'() {

        given:
        migrated("LEI")

        when:
        //2 matched and active, 1 matched and inactive and 1 unknown offender
        def response = restTemplate.exchange("/key-worker/LEI/offenders", HttpMethod.POST,
                createHeaderEntity(['A1176RS', "A5576RS", "A6676RS", "unknown"]), String.class)

        then:
        response.statusCode == HttpStatus.OK
        def allocationList = jsonSlurper.parseText(response.body)
        allocationList.collect { it.offenderNo } == ['A1176RS', 'A5576RS']
    }

    def 'Retrieve offender keyworker details using POST endpoint - no offender list provided'() {

        given:
        migrated("LEI")

        when:
        def response = restTemplate.exchange("/key-worker/LEI/offenders", HttpMethod.POST, createHeaderEntity("[]"), String.class)

        then:
        response.statusCode == HttpStatus.BAD_REQUEST
    }

    def 'Retrieve single offender keyworker details using GET endpoint'() {

        given:
        elite2api.stubOffenderKeyWorker('A6676RS')

        when:
        def response = restTemplate.exchange("/key-worker/offender/A6676RS", HttpMethod.GET, createHeaderEntity(), String.class)

        then:
        response.statusCode == HttpStatus.OK
        def allocationList = jsonSlurper.parseText(response.body)
        allocationList == [staffId: -4, firstName: 'John', lastName: 'Henry', email: 'john@justice.gov.uk']
    }
}
