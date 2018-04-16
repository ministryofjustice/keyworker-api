package uk.gov.justice.digital.hmpps.keyworker.integration.specs

import groovy.json.JsonSlurper
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus

class AvailableKeyworkersSpecification extends TestSpecification {

    def jsonSlurper = new JsonSlurper()

    def 'Available keyworkers - decorated with defaults after migration'() {

        given:
        migrated("LEI")
        elite2api.stubAvailableKeyworkers("LEI")

        when:
        def response = restTemplate.exchange("/key-worker/LEI/available", HttpMethod.GET, createHeaderEntity("headers"), String.class)

        then:
        response.statusCode == HttpStatus.OK
        def allocationList = jsonSlurper.parseText(response.body)
        allocationList.size() == 4
        //result sorted by surname
        allocationList[0].agencyId == 'LEI'
        allocationList[0].autoAllocationAllowed == true //no current record in database - default
        allocationList[0].status == 'ACTIVE' //no current record in database - default
        allocationList[0].capacity == 6 //no current record in database - default
        allocationList[0].numberAllocated == 0 //no allocations migrated for this user
        allocationList[0].firstName == 'HPA'
        allocationList[0].lastName == 'AUser'
    }
}



