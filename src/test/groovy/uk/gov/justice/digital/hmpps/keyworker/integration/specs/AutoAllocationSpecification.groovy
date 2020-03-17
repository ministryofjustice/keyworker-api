package uk.gov.justice.digital.hmpps.keyworker.integration.specs


import java.time.LocalDate
import java.time.format.DateTimeFormatter

import static org.assertj.core.api.Assertions.assertThat

class AutoAllocationSpecification extends TestSpecification {

    final static TODAY = LocalDate.now().format(DateTimeFormatter.ISO_DATE)

    def "Allocation service reports ok"() {

        given:
        migratedForAutoAllocation("SYI") // a prison which does not affect other tests
        elite2api.stubAvailableKeyworkersForAutoAllocation("SYI")
        elite2api.stubOffendersAtLocationForAutoAllocation("SYI")
        capacityOf1002is1And1001is3()

        when:
        postForEntity("/key-worker/SYI/allocate/start", createHeaderEntity(), "headers")
                .expectStatus().is2xxSuccessful()
                .expectBody().is(10)
        postForEntity("/key-worker/SYI/allocate/confirm", createHeaderEntity(), "headers")
                .expectStatus().is2xxSuccessful()
                .expectBody().is(10)
        getForEntity("/key-worker/SYI/allocations", createHeaderEntity())
                .expectStatus().is2xxSuccessful()
                .expectBody()
                .jsonPath('$.length()').isEqualTo(11)
                .jsonPath('$[0].offenderNo').isEqualTo('ALLOCED1')// KW is already 1001
                .jsonPath('$[0].allocationType').isEqualTo('M')

                .jsonPath('$[1].offenderNo').isEqualTo('UNALLOC1')
                .jsonPath('$[1].staffId').isEqualTo(1002)// 1001 not chosen as its no allocated > 0
                .jsonPath('$[1].allocationType').isEqualTo('A')
                .jsonPath('$[1].assigned').value { assigned -> assertThat(assigned).contains(TODAY) }

                .jsonPath('$[2].offenderNo').isEqualTo('UNALLOC2')
                .jsonPath('$[2].staffId').isEqualTo(1003)

                .jsonPath('$[3].offenderNo').isEqualTo('UNALLOC3')
                .jsonPath('$[3].staffId').isEqualTo(1001)// Now chosen in staffId numerical order
                  // 1001 is not bypassed due to an old allocation because this was NOT auto!

                .jsonPath('$[4].offenderNo').isEqualTo('UNALLOC4')
                .jsonPath('$[4].staffId').isEqualTo(1003)// 1002 is now full

                .jsonPath('$[5].offenderNo').isEqualTo('UNALLOC5')
                .jsonPath('$[5].staffId').isEqualTo(1001)

                .jsonPath('$[6].offenderNo').isEqualTo('UNALLOC6')
                .jsonPath('$[6].staffId').isEqualTo(1003)

                .jsonPath('$[7].offenderNo').isEqualTo('UNALLOC7')
                .jsonPath('$[7].staffId').isEqualTo(1001)

                .jsonPath('$[8].offenderNo').isEqualTo('UNALLOC8')
                .jsonPath('$[8].staffId').isEqualTo(1003)

                .jsonPath('$[9].offenderNo').isEqualTo('UNALLOC9')
                .jsonPath('$[9].staffId').isEqualTo(1003)// 1001 is now full

                .jsonPath('$[10].offenderNo').isEqualTo('EXPIRED1')// KW set to previous: 1002, despite being full
                .jsonPath('$[10].staffId').isEqualTo(1002)
                .jsonPath('$[10].allocationType').isEqualTo("A")
                .jsonPath('$[1].assigned').value { assigned -> assertThat(assigned).contains(TODAY) }

        then:
        noExceptionThrown()
    }

    private void capacityOf1002is1And1001is3() {
        postForEntity("/key-worker/1002/prison/SYI", createHeaderEntity(), "{\"capacity\": 1, \"status\": \"ACTIVE\"}")
                .expectStatus().is2xxSuccessful()
        postForEntity("/key-worker/1001/prison/SYI", createHeaderEntity(), "{\"capacity\": 3, \"status\": \"ACTIVE\"}")
                .expectStatus().is2xxSuccessful()
    }
}
