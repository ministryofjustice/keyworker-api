package uk.gov.justice.digital.hmpps.keyworker.integration.specs

class PingSpecification extends TestSpecification {

    def "Ping page returns pong"() {
        when:
        getForEntity("/ping", {})
                .expectStatus().is2xxSuccessful()
                .expectHeader().contentType("text/plain;charset=UTF8")
                .expectBody().is("pong")

        then:
        noExceptionThrown()
    }
}
