package uk.gov.justice.digital.hmpps.keyworker.integration.specs

class KeyworkerDetailsSpecification extends TestSpecification {

    def 'key worker details happy path'() {

        given:
        migrated("LEI")
        elite2api.stubKeyworkerDetails("LEI", -5)

        when:
        getForEntity("/key-worker/-5/prison/LEI", createHeaderEntity())
                .expectStatus().is2xxSuccessful()
                .expectBody()
                .jsonPath('$.agencyId').isEqualTo('LEI')
                .jsonPath('$.autoAllocationAllowed').isEqualTo(true) //no current record in database - default
                .jsonPath('$.status').isEqualTo('ACTIVE') //no current record in database - default
                .jsonPath('$.capacity').isEqualTo(6) //no current record in database - default
                .jsonPath('$.numberAllocated').isEqualTo(3) //after migration -5 has 3 active allocations
                .jsonPath('$.firstName').isEqualTo('Another')
                .jsonPath('$.lastName').isEqualTo('CUser')
                .jsonPath('$.activeDate').doesNotExist()

        then:
        noExceptionThrown()
    }

    def 'key worker details - keyworker not available for prison - defaults to retrieve basic details (from other prison)'() {

        given:
        migrated("LEI")
        elite2api.stubKeyworkerDetails_emptyListResponse("LEI", -5)  //lookup for prison fails to retrieve the keyworker details  (no longer working for current agency)
        elite2api.stubKeyworkerDetails_basicDetailsOnly(-5)

        when:
        getForEntity("/key-worker/-5/prison/LEI", createHeaderEntity())
                .expectStatus().is2xxSuccessful()
                .expectBody()
                .jsonPath('$.agencyId').doesNotExist() //basic details do not return agency id - we are only retreiving these details to enable displaying of keyworker name
                .jsonPath('$.numberAllocated').doesNotExist() //unable to determine allocations without agencyId
                .jsonPath('$.firstName').isEqualTo('Another')
                .jsonPath('$.lastName').isEqualTo('CUser')

        then:
        noExceptionThrown()
    }
}



