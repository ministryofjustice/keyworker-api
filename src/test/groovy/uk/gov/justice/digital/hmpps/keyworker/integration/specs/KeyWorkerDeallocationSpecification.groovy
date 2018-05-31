package uk.gov.justice.digital.hmpps.keyworker.integration.specs

import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus

class KeyWorkerDeallocationSpecification extends TestSpecification {

    def 'Existing Active Offender can be de-allocated'() {

        given:
        migrated("LEI")

        when:
        //deallocate an active offender
        def response = restTemplate.exchange("/key-worker/deallocate/A1234XY", HttpMethod.PUT,
                createHeaderEntity(), Void.class)

        then:
        response.statusCode == HttpStatus.OK
    }

    def 'De-allocate inactive offender'() {

        given:
        migrated("LEI")

        when:
        def response = restTemplate.exchange("/key-worker/deallocate/A6676RS", HttpMethod.PUT, createHeaderEntity(), Void.class)

        then:
        response.statusCode == HttpStatus.NOT_FOUND
    }
}
