package uk.gov.justice.digital.hmpps.keyworker.integration.specs

class KeyWorkerDeallocationSpecification extends TestSpecification {

    def 'Existing Active Offender can be de-allocated'() {

        given:
        migrated("LEI")

        when:
        //deallocate an active offender
        putForEntity("/key-worker/deallocate/A1234XY", createHeaderEntity(), "")
                .expectStatus().is2xxSuccessful()

        then:
        noExceptionThrown()
    }

    def 'De-allocate inactive offender'() {

        given:
        migrated("LEI")

        when:
        putForEntity("/key-worker/deallocate/A6676RS", createHeaderEntity(), "")
                .expectStatus().isNotFound()

        then:
        noExceptionThrown()
    }
}
