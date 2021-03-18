package uk.gov.justice.digital.hmpps.keyworker.integration.specs

class KeyworkerSearchSpecification extends TestSpecification {
    def 'keyworker search - decorated with defaults after migration'() {

        given:
        migrated("LEI")
        elite2api.stubKeyworkerSearch("LEI", "User")
        elite2api.stubCaseNoteUsage()

        when:
        getForEntity("/key-worker/LEI/members?nameFilter=User&statusFilter=ACTIVE", createHeaderEntity())
                .expectStatus().is2xxSuccessful()
                .expectBody()
                .jsonPath('$[0].lastName').isEqualTo('CUser')
                .jsonPath('$[0].capacity').isEqualTo(6)
                .jsonPath('$[0].numberAllocated').isEqualTo(3)
                .jsonPath('$[0].numKeyWorkerSessions').isEqualTo(4)

        then:
        noExceptionThrown()
    }
}



