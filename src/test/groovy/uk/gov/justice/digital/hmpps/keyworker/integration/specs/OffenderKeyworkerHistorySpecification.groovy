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
        keyWorkerHistory.allocationHistory[0].active == false
        keyWorkerHistory.allocationHistory[0].allocationReason == 'Manual'
        keyWorkerHistory.allocationHistory[0].userId.staffId == -2
        keyWorkerHistory.allocationHistory[0].lastModifiedByUser.username == 'omicadmin'
        keyWorkerHistory.allocationHistory[1].staffId == -5
        keyWorkerHistory.allocationHistory[1].userId.staffId == -2
        keyWorkerHistory.allocationHistory[1].active == false
        keyWorkerHistory.allocationHistory[1].allocationReason == 'Manual'
        keyWorkerHistory.allocationHistory[1].lastModifiedByUser.username == 'omicadmin'
    }

    def 'Allocation and Deallocation'() {

        given:
        migrated("LEI")
        elite2api.stubKeyworkerDetails("LEI", -2, )
        elite2api.stubKeyworkerDetails_basicDetailsOnly(-4)
        elite2api.stubKeyworkerDetails_basicDetailsOnly(-2)
        elite2api.stubStaffUserDetails("omicadmin")
        elite2api.stubOffenderLookup("LEI", "A1234XX")
        elite2api.stubOffenderLookup("LEI", "A1234XZ")
        elite2api.stubStaffUserDetails("ITAG_USER")
        elite2api.stubStaffUserDetails("ELITE2_API_USER")

        when: 'Allocating'
        restTemplate.exchange("/key-worker/allocate", HttpMethod.POST, createHeaderEntity("{\"allocationReason\": \"MANUAL\"," +
                "  \"allocationType\": \"M\"," +
                "  \"offenderNo\": \"A1234XX\"," +
                "  \"prisonId\": \"LEI\"," +
                "  \"staffId\": -2}"), String.class)
        def response = restTemplate.exchange("/key-worker/allocation-history/A1234XX", HttpMethod.GET,
                createHeaderEntity(), String.class)

        then: 'the history size increases'
        response.statusCode == HttpStatus.OK
        def keyWorkerHistory = jsonSlurper.parseText(response.body)
        keyWorkerHistory.offender.offenderNo == 'A1234XX'
        keyWorkerHistory.allocationHistory.size() == 2
        keyWorkerHistory.allocationHistory[0].staffId == -2
        keyWorkerHistory.allocationHistory[0].active == true
        keyWorkerHistory.allocationHistory[0].allocationReason == 'Manual'
        keyWorkerHistory.allocationHistory[0].lastModifiedByUser.username == 'ITAG_USER'
        keyWorkerHistory.allocationHistory[0].createdByUser.username == 'ITAG_USER'

       // Note this test depends on the previous allocation
        when: 'Deallocating'
        restTemplate.exchange("/key-worker/deallocate/A1234XZ", HttpMethod.PUT, createHeaderEntity(), Void.class)
        def response2 = restTemplate.exchange("/key-worker/allocation-history/A1234XZ", HttpMethod.GET,
                createHeaderEntity(), String.class)

        then: 'The last record becomes inactive'
        response2.statusCode == HttpStatus.OK
        def keyWorkerHistory2 = jsonSlurper.parseText(response2.body)
        keyWorkerHistory2.offender.offenderNo == 'A1234XZ'
        keyWorkerHistory2.allocationHistory.size() == 1
        keyWorkerHistory2.allocationHistory[0].staffId == -4
        keyWorkerHistory2.allocationHistory[0].active == false
        keyWorkerHistory2.allocationHistory[0].lastModifiedByUser.username == 'ITAG_USER'
        keyWorkerHistory2.allocationHistory[0].createdByUser.username == 'omicadmin'
        keyWorkerHistory2.allocationHistory[0].deallocationReason == 'Manual'
    }
}
