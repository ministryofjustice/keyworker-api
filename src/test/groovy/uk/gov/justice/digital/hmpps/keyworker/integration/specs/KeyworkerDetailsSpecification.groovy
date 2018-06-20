package uk.gov.justice.digital.hmpps.keyworker.integration.specs

import groovy.json.JsonSlurper
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus

class KeyworkerDetailsSpecification extends TestSpecification {

    def jsonSlurper = new JsonSlurper()

    def 'key worker details happy path'() {

        given:
        migrated("LEI")
        elite2api.stubKeyworkerDetails("LEI", -5)

        when:
        def response = restTemplate.exchange("/key-worker/-5/prison/LEI", HttpMethod.GET, createHeaderEntity("headers"), String.class)

        then:
        response.statusCode == HttpStatus.OK
        def keyworkerDetails = jsonSlurper.parseText(response.body)
        keyworkerDetails.agencyId == 'LEI'
        keyworkerDetails.autoAllocationAllowed == true //no current record in database - default
        keyworkerDetails.status == 'ACTIVE' //no current record in database - default
        keyworkerDetails.capacity == 6 //no current record in database - default
        keyworkerDetails.numberAllocated == 3 //after migration -5 has 3 active allocations
        keyworkerDetails.firstName == 'Another'
        keyworkerDetails.lastName == 'CUser'
        keyworkerDetails.activeDate == null
    }

    def 'key worker details - keyworker not available for prison - defaults to retrieve basic details (from other prison)'() {

        given:
        migrated("LEI")
        elite2api.stubKeyworkerDetails_emptyListResponse("LEI", -5)  //lookup for prison fails to retrieve the keyworker details  (no longer working for current agency)
        elite2api.stubKeyworkerDetails_basicDetailsOnly(-5)

        when:
        def response = restTemplate.exchange("/key-worker/-5/prison/LEI", HttpMethod.GET, createHeaderEntity("headers"), String.class)

        then:
        response.statusCode == HttpStatus.OK
        def keyworkerDetails = jsonSlurper.parseText(response.body)
        keyworkerDetails.agencyId == null //basic details do not return agency id - we are only retreiving these details to enable displaying of keyworker name
        keyworkerDetails.numberAllocated == null //unable to determine allocations without agencyId
        keyworkerDetails.firstName == 'Another'
        keyworkerDetails.lastName == 'CUser'
        keyworkerDetails.agencyId == null
    }
}



