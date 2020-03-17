package uk.gov.justice.digital.hmpps.keyworker.integration.specs

class AvailableKeyworkersSpecification extends TestSpecification {

    def 'Available keyworkers - decorated with defaults after migration'() {

        given:
        migrated("LEI")
        elite2api.stubAvailableKeyworkers("LEI")

        when:
        getForEntity("/key-worker/LEI/available", createHeaderEntity())
                .expectStatus().is2xxSuccessful()
                .expectBody()
                .jsonPath('$.length()').isEqualTo(4)
                .jsonPath('$[0].agencyId').isEqualTo('LEI')
                .jsonPath('$[0].autoAllocationAllowed').isEqualTo(true) //no current record in database - default
                .jsonPath('$[0].status').isEqualTo('ACTIVE') //no current record in database - default
                .jsonPath('$[0].capacity').isEqualTo(6) //no current record in database - default
                .jsonPath('$[0].firstName').isEqualTo('HPA') //no allocations migrated for this user
                .jsonPath('$[0].lastName').isEqualTo('AUser')


        then:
        noExceptionThrown()
    }
}



