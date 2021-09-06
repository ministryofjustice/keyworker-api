package uk.gov.justice.digital.hmpps.keyworker.integration.specs


import java.time.LocalDate
import java.time.format.DateTimeFormatter

import static uk.gov.justice.digital.hmpps.keyworker.services.KeyworkerService.KEYWORKER_CASENOTE_TYPE

class KeyworkerStatsSpecification extends TestSpecification {

    int staffId = -5

    def "should return a staff members stats"() {

        def formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        def toDate = LocalDate.now()
        def fromDate = toDate.minusMonths(1)
        def fromDateStr = fromDate.format(formatter)
        def toDateStr = toDate.format(formatter)

        def offenderNos = ["A9876RS", "A1176RS", "A5576RS"]

        def caseNoteUsageCounts = [
                ["offenderNo": "A1176RS", "caseNoteType": "KA", "caseNoteSubType": "KS", "latestCaseNote": fromDate.plusDays(1).format(formatter), "numCaseNotes": 4],
                ["offenderNo": "A5576RS", "caseNoteType": "KA", "caseNoteSubType": "KS", "latestCaseNote": fromDate.plusDays(5).format(formatter), "numCaseNotes": 2],
                ["offenderNo": "A9876RS", "caseNoteType": "KA", "caseNoteSubType": "KS", "latestCaseNote": fromDate.plusDays(15).format(formatter), "numCaseNotes": 3],
                ["offenderNo": "A1176RS", "caseNoteType": "KA", "caseNoteSubType": "KE", "latestCaseNote": fromDate.plusDays(21).format(formatter), "numCaseNotes": 1],
                ["offenderNo": "A5576RS", "caseNoteType": "KA", "caseNoteSubType": "KE", "latestCaseNote": fromDate.plusDays(6).format(formatter), "numCaseNotes": 1],
                ["offenderNo": "A9876RS", "caseNoteType": "KA", "caseNoteSubType": "KE", "latestCaseNote": fromDate.plusDays(8).format(formatter), "numCaseNotes": 1],
        ]
        given:
        migrated("LEI")
        prisonapi.stubKeyworkerDetails_basicDetailsOnly(staffId)
        prisonapi.stubCaseNoteUsagePrisonerFor(offenderNos, staffId, KEYWORKER_CASENOTE_TYPE, fromDateStr, toDateStr, caseNoteUsageCounts)

        when: "a request for stats is made for the a member of staff"
        getForEntity("/key-worker-stats/${staffId}/prison/LEI?fromDate=${fromDateStr}&toDate=${toDateStr}".toString(), createHeaderEntity())
                .expectBody()
                .jsonPath('$.caseNoteSessionCount').isEqualTo(9)
                .jsonPath('$.caseNoteEntryCount').isEqualTo(3)
                .jsonPath('$.projectedKeyworkerSessions').isEqualTo(12)
                .jsonPath('$.complianceRate').isEqualTo(75)

        then: "a count of entry and session case notes is returned"
        noExceptionThrown()
    }

}
