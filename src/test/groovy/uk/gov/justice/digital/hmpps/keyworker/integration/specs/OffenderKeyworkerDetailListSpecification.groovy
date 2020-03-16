package uk.gov.justice.digital.hmpps.keyworker.integration.specs

class OffenderKeyworkerDetailListSpecification extends TestSpecification {

    def 'Retrieve offender keyworker details using POST endpoint'() {

        given:
        migrated("LEI")

        when:
        //2 matched and active, 1 matched and inactive and 1 unknown offender
        postForEntity("/key-worker/LEI/offenders", createHeaderEntity(), ['A1176RS', "A5576RS", "A6676RS", "unknown"])
                .expectStatus().is2xxSuccessful()
                .expectBody()
                .jsonPath('$..offenderNo').isEqualTo(['A1176RS', 'A5576RS'])

        then:
        noExceptionThrown()
    }

    def 'Retrieve offender keyworker details using POST endpoint - no offender list provided'() {

        given:
        migrated("LEI")

        when:
        postForEntity("/key-worker/LEI/offenders", createHeaderEntity(), "[]")
                .expectStatus().isBadRequest()

        then:
        noExceptionThrown()
    }

    def 'Retrieve single offender keyworker details using GET endpoint'() {

        given:
        elite2api.stubOffenderKeyWorker('A6676RS')
        elite2api.stubPrisonerLookup('A6676RS')

        when:
        getForEntity("/key-worker/offender/A6676RS", createHeaderEntity())
                .expectStatus().is2xxSuccessful()
                .expectBody()
                .jsonPath('$.staffId').isEqualTo(-4)
                .jsonPath('$.firstName').isEqualTo('John')
                .jsonPath('$.lastName').isEqualTo('Henry')
                .jsonPath('$.email').isEqualTo('john@justice.gov.uk')

        then:
        noExceptionThrown()
    }
}
