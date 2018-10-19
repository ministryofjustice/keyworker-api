package uk.gov.justice.digital.hmpps.keyworker.integration.specs

import groovy.json.JsonSlurper
import org.springframework.http.HttpMethod

import java.time.LocalDate
import java.time.format.DateTimeFormatter

import static uk.gov.justice.digital.hmpps.keyworker.services.KeyworkerService.KEYWORKER_CASENOTE_TYPE

class KeyworkerStatsSpecification extends TestSpecification {

    int staffId = -5

    def "should return a staff members stats"() {

        def formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        def toDate = LocalDate.now()
        def fromDate = toDate.minusMonths(1);
        def fromDateStr = fromDate.format(formatter);
        def toDateStr = toDate.format(formatter);
        def jsonSlurper = new JsonSlurper()

        def offenderNos = [ "A9876RS","A1176RS","A5576RS" ]

        def caseNoteUsageCounts = [
                [ "offenderNo": "A1176RS", "caseNoteType": "KA", "caseNoteSubType": "KS", "latestCaseNote": fromDate.plusDays(1).format(formatter), "numCaseNotes": 4 ],
                [ "offenderNo": "A5576RS", "caseNoteType": "KA", "caseNoteSubType": "KS", "latestCaseNote": fromDate.plusDays(5).format(formatter), "numCaseNotes": 2 ],
                [ "offenderNo": "A9876RS", "caseNoteType": "KA", "caseNoteSubType": "KS", "latestCaseNote": fromDate.plusDays(15).format(formatter), "numCaseNotes": 3 ],
                [ "offenderNo": "A1176RS", "caseNoteType": "KA", "caseNoteSubType": "KE", "latestCaseNote": fromDate.plusDays(21).format(formatter), "numCaseNotes": 1 ],
                [ "offenderNo": "A5576RS", "caseNoteType": "KA", "caseNoteSubType": "KE", "latestCaseNote": fromDate.plusDays(6).format(formatter), "numCaseNotes": 1 ],
                [ "offenderNo": "A9876RS", "caseNoteType": "KA", "caseNoteSubType": "KE", "latestCaseNote": fromDate.plusDays(8).format(formatter), "numCaseNotes": 1 ],
        ]
        given:
        migrated("LEI")
        elite2api.stubKeyworkerDetails_basicDetailsOnly(staffId)
        elite2api.stubCaseNoteUsagePrisonerFor(offenderNos, KEYWORKER_CASENOTE_TYPE, fromDateStr, toDateStr, caseNoteUsageCounts)

        when: "a request for stats is made for the a member of staff"
        def response = restTemplate.exchange("/key-worker-stats/${staffId}/prison/LEI?fromDate=${fromDateStr}&toDate=${toDateStr}", HttpMethod.GET, createHeaderEntity("headers"), String.class)

        then: "a count of entry and session case notes is returned"
        def stats = jsonSlurper.parseText(response.body)
        stats.caseNoteSessionCount == 9
        stats.caseNoteEntryCount == 3
        stats.projectedKeyworkerSessions == 12
        stats.complianceRate == 75
    }

}
