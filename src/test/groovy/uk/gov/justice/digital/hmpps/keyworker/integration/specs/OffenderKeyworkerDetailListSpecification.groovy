package uk.gov.justice.digital.hmpps.keyworker.integration.specs

import groovy.json.JsonSlurper
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import uk.gov.justice.digital.hmpps.keyworker.dto.OffenderKeyworkerDto

class OffenderKeyworkerDetailListSpecification extends TestSpecification {

    def jsonSlurper = new JsonSlurper()

    def 'Retrieve offender keyworker details using POST endpoint'() {

        given:
        migrated("LEI")

        when:
        //2 matched and active, 1 matched and inactive and 1 unknown offender
        def response = restTemplate.exchange("/key-worker/LEI/offenders", HttpMethod.POST, createHeaderEntity([ 'A1176RS', "A5576RS", "A6676RS", "unknown"]), String.class)

        then:
        response.statusCode == HttpStatus.OK
        def allocationList = jsonSlurper.parseText(response.body)
        allocationList.size() == 2
        allocationList[0].offenderNo == 'A1176RS'
        allocationList[1].offenderNo == 'A5576RS'
    }

    def 'Retrieve offender keyworker details using POST endpoint - no offender list provided'() {

        given:
        migrated("LEI")

        when:
        def response = restTemplate.exchange("/key-worker/LEI/offenders", HttpMethod.POST, createHeaderEntity("[]"), String.class)

        then:
        response.statusCode == HttpStatus.BAD_REQUEST
    }
}



