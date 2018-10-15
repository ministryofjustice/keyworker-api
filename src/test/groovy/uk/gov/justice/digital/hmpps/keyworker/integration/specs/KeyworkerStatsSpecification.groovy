package uk.gov.justice.digital.hmpps.keyworker.integration.specs

import groovy.json.JsonSlurper
import org.springframework.http.HttpMethod

class KeyworkerStatsSpecification extends TestSpecification {

    int staffId = -5

    def "should get a bad request when the date parameters are missing from the request"() {
        given:
        migrated("LEI")
        elite2api.stubKeyworkerDetails_basicDetailsOnly(staffId)

        when: "a request for stats is made for the a member of staff"
        def response = restTemplate.exchange("/key-worker/${staffId}/prison/LEI/stats", HttpMethod.GET, createHeaderEntity("headers"), String.class)

        then: "the response status code should be 400"
        response.statusCode.value() == 400
    }

    def "should return the correct case note counts"() {

        def jsonSlurper = new JsonSlurper()
        def fromDate = "2018-07-10"
        def toDate = "2018-07-10"

        def sessionCaseNotes = [
                [ "staffId": staffId,"caseNoteType": "KA", "caseNoteSubType": "KS","latestCaseNote": toDate, "numCaseNotes": 3 ],
        ]

        def entryCaseNotes = [
                [ "staffId": staffId,"caseNoteType": "KA", "caseNoteSubType": "KA","latestCaseNote": toDate, "numCaseNotes": 2 ],
        ]

        given:
        migrated("LEI")
        elite2api.stubKeyworkerDetails_basicDetailsOnly(staffId)
        elite2api.stubCaseNoteUsageFor(staffId, "KA", "KS", fromDate, toDate,sessionCaseNotes)
        elite2api.stubCaseNoteUsageFor(staffId, "KA", "KA", fromDate, toDate,entryCaseNotes)

        when: "a request for stats is made for the a member of staff"
        def response = restTemplate.exchange("/key-worker/${staffId}/prison/LEI/stats?fromDate=${fromDate}&toDate=${toDate}", HttpMethod.GET, createHeaderEntity("headers"), String.class)

        then: "a count of entry and session case notes is returned"
        def stats = jsonSlurper.parseText(response.body)
        stats.caseNoteSessionCount == 3
        stats.caseNoteEntryCount == 2
        stats.projectedKeyworkerSessions == 0
        stats.complianceRate == 0
    }

}
