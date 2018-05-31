package uk.gov.justice.digital.hmpps.keyworker.integration.specs

import groovy.json.JsonSlurper
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus

class OffenderKeyworkerHistorySpecification extends TestSpecification {

    def jsonSlurper = new JsonSlurper()

    def 'History of inactive offender'() {

        given:
        migrated("LEI")
        elite2api.stubKeyworkerDetails_basicDetailsOnly(-5)
        elite2api.stubStaffUserDetails("omicadmin")
        elite2api.stubOffenderLookup("LEI", "A6676RS")
        elite2api.stubStaffUserDetails("ITAG_USER")


        when:
        //get allocation history
        def response = restTemplate.exchange("/key-worker/allocation-history/A6676RS", HttpMethod.GET,
                createHeaderEntity(), String.class)

        then:
        response.statusCode == HttpStatus.OK
        def keyWorkerHistory = jsonSlurper.parseText(response.body)
        keyWorkerHistory.offender.offenderNo == 'A6676RS'
        keyWorkerHistory.allocationHistory.size() == 2
        keyWorkerHistory.allocationHistory[0].staffId == -5
        keyWorkerHistory.allocationHistory[0].active == 'No'
        keyWorkerHistory.allocationHistory[0].userId.staffId == -2
        keyWorkerHistory.allocationHistory[0].lastModifiedByUser.username == 'omicadmin'
        keyWorkerHistory.allocationHistory[1].staffId == -5
        keyWorkerHistory.allocationHistory[1].userId.staffId == -2
        keyWorkerHistory.allocationHistory[1].active == 'No'
        keyWorkerHistory.allocationHistory[1].lastModifiedByUser.username == 'omicadmin'
    }

    def 'Allocating increases the history size'() {

        given:
        migrated("LEI")
        elite2api.stubKeyworkerDetails("LEI", -2, )
        elite2api.stubKeyworkerDetails_basicDetailsOnly(-5)
        elite2api.stubKeyworkerDetails_basicDetailsOnly(-2)
        elite2api.stubStaffUserDetails("omicadmin")
        elite2api.stubOffenderLookup("LEI", "A1176RS")
        elite2api.stubStaffUserDetails("ITAG_USER")

        when:

        restTemplate.exchange("/key-worker/allocate", HttpMethod.POST, createHeaderEntity("{\"allocationReason\": \"MANUAL\"," +
                "  \"allocationType\": \"M\"," +
                "  \"offenderNo\": \"A1176RS\"," +
                "  \"prisonId\": \"LEI\"," +
                "  \"staffId\": -2}"), String.class)
        def response = restTemplate.exchange("/key-worker/allocation-history/A1176RS", HttpMethod.GET,
                createHeaderEntity(), String.class)

        then:
        response.statusCode == HttpStatus.OK
        def keyWorkerHistory = jsonSlurper.parseText(response.body)
        keyWorkerHistory.offender.offenderNo == 'A1176RS'
        keyWorkerHistory.allocationHistory.size() == 2
        keyWorkerHistory.allocationHistory[0].staffId == -2
        keyWorkerHistory.allocationHistory[0].active == 'Yes'
        keyWorkerHistory.allocationHistory[1].staffId == -5
        keyWorkerHistory.allocationHistory[1].active == 'No'
    }

    def 'Deallocation makes last record inactive'() {

        given:
        migrated("LEI")
        elite2api.stubKeyworkerDetails_basicDetailsOnly(-5)
        elite2api.stubStaffUserDetails("omicadmin")
        elite2api.stubOffenderLookup("LEI", "A1176RS")
        elite2api.stubStaffUserDetails("ITAG_USER")

        when:

        restTemplate.exchange("/key-worker/deallocate/A1176RS", HttpMethod.PUT, createHeaderEntity(), Void.class)
        def response = restTemplate.exchange("/key-worker/allocation-history/A1176RS", HttpMethod.GET,
                createHeaderEntity(), String.class)

        then:
        response.statusCode == HttpStatus.OK
        def keyWorkerHistory = jsonSlurper.parseText(response.body)
        keyWorkerHistory.offender.offenderNo == 'A1176RS'
        keyWorkerHistory.allocationHistory.size() == 2
        keyWorkerHistory.allocationHistory[0].staffId == -2
        keyWorkerHistory.allocationHistory[0].active == 'No'
        keyWorkerHistory.allocationHistory[1].staffId == -5
        keyWorkerHistory.allocationHistory[1].active == 'No'
    }
}
