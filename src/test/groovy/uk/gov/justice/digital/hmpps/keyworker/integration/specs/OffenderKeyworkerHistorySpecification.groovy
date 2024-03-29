package uk.gov.justice.digital.hmpps.keyworker.integration.specs

class OffenderKeyworkerHistorySpecification extends TestSpecification {

    def 'History of inactive offender'() {

        given:
        migrated("LEI")
        prisonApi.stubOffenderAllocationHistory("A6676RS")
        prisonApi.stubKeyworkerDetails_basicDetailsOnly(-5)
        prisonApi.stubStaffUserDetails("omicadmin")
        prisonApi.stubPrisonerLookup("A6676RS")
        prisonApi.stubStaffUserDetails("ITAG_USER")


        when:
        //get allocation history
        getForEntity("/key-worker/allocation-history/A6676RS", createHeaderEntity())
                .expectStatus().is2xxSuccessful()
                .expectBody()
                .jsonPath('$.allocationHistory.length()').isEqualTo(2)
                .jsonPath('$.allocationHistory[0].staffId').isEqualTo(-5)
                .jsonPath('$.allocationHistory[0].active').isEqualTo(false)
                .jsonPath('$.allocationHistory[0].allocationReason').isEqualTo('Manual')
                .jsonPath('$.allocationHistory[0].userId.staffId').isEqualTo(-2)
                .jsonPath('$.allocationHistory[0].lastModifiedByUser.username').isEqualTo('omicadmin')
                .jsonPath('$.allocationHistory[1].staffId').isEqualTo(-5)
                .jsonPath('$.allocationHistory[1].userId.staffId').isEqualTo(-2)
                .jsonPath('$.allocationHistory[1].active').isEqualTo(false)
                .jsonPath('$.allocationHistory[1].allocationReason').isEqualTo('Manual')
                .jsonPath('$.allocationHistory[1].lastModifiedByUser.username').isEqualTo('omicadmin')

        then:
        noExceptionThrown()
    }

    def 'Allocation and Deallocation'() {

        given:
        migrated("LEI")
        prisonApi.stubOffenderAllocationHistory("A1234XX")
        prisonApi.stubOffenderAllocationHistory("A1234XZ")

        prisonApi.stubKeyworkerDetails("LEI", -2,)
        prisonApi.stubKeyworkerDetails_basicDetailsOnly(-4)
        prisonApi.stubKeyworkerDetails_basicDetailsOnly(-2)
        prisonApi.stubStaffUserDetails("omicadmin")
        prisonApi.stubOffenderLookup("LEI", "A1234XX")
        prisonApi.stubOffenderLookup("LEI", "A1234XZ")
        prisonApi.stubPrisonerLookup("A1234XX")
        prisonApi.stubPrisonerLookup("A1234XZ")
        prisonApi.stubStaffUserDetails("ITAG_USER")
        prisonApi.stubStaffUserDetails("prison_API_USER")

        when: 'Allocating'
        postForEntity("/key-worker/allocate", createHeaderEntity(), "{\"allocationReason\": \"MANUAL\"," +
                "  \"allocationType\": \"M\"," +
                "  \"offenderNo\": \"A1234XX\"," +
                "  \"prisonId\": \"LEI\"," +
                "  \"staffId\": -2}")

        getForEntity("/key-worker/allocation-history/A1234XX", createHeaderEntity())
                .expectStatus().is2xxSuccessful()
                .expectBody()
                .jsonPath('$.offender.offenderNo').isEqualTo('A1234XX')
                .jsonPath('$.allocationHistory.length()').isEqualTo(2)
                .jsonPath('$.allocationHistory[0].staffId').isEqualTo(-2)
                .jsonPath('$.allocationHistory[0].active').isEqualTo(true)
                .jsonPath('$.allocationHistory[0].allocationReason').isEqualTo('Manual')
                .jsonPath('$.allocationHistory[0].lastModifiedByUser.username').isEqualTo('ITAG_USER')
                .jsonPath('$.allocationHistory[0].createdByUser.username').isEqualTo('ITAG_USER')

        then: 'the history size increases'
        noExceptionThrown()

        // Note this test depends on the previous allocation
        when: 'Deallocating'
        putForEntity("/key-worker/deallocate/A1234XZ", createHeaderEntity(), "")
        getForEntity("/key-worker/allocation-history/A1234XZ", createHeaderEntity())
                .expectStatus().is2xxSuccessful()
                .expectBody()
                .jsonPath('$.offender.offenderNo').isEqualTo('A1234XZ')
                .jsonPath('$.allocationHistory.length()').isEqualTo(1)
                .jsonPath('$.allocationHistory[0].staffId').isEqualTo(-4)
                .jsonPath('$.allocationHistory[0].active').isEqualTo(false)
                .jsonPath('$.allocationHistory[0].lastModifiedByUser.username').isEqualTo('ITAG_USER')
                .jsonPath('$.allocationHistory[0].createdByUser.username').isEqualTo('omicadmin')
                .jsonPath('$.allocationHistory[0].deallocationReason').isEqualTo('Manual')

        then: 'The last record becomes inactive'
        noExceptionThrown()
    }
}
