package uk.gov.justice.digital.hmpps.keyworker.integration.specs

import groovy.json.JsonSlurper
import org.junit.Assert
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus

import java.time.LocalDate
import java.time.format.DateTimeFormatter

class AutoAllocationSpecification extends TestSpecification {

    final static TODAY = LocalDate.now().format(DateTimeFormatter.ISO_DATE)
    def jsonSlurper = new JsonSlurper()

    def "Allocation service reports ok"() {

        given:
        migratedForAutoAllocation("SYI") // a prison which does not affect other tests
        elite2api.stubAvailableKeyworkersForAutoAllocation("SYI")
        elite2api.stubOffendersAtLocationForAutoAllocation("SYI")
        capacityOf1002is1And1001is3()

        when:
        def response = restTemplate.exchange("/key-worker/SYI/allocate/start", HttpMethod.POST, createHeaderEntity("headers"), String.class)
        def confirm = restTemplate.exchange("/key-worker/SYI/allocate/confirm", HttpMethod.POST, createHeaderEntity("headers"), String.class)
        def allocations = restTemplate.exchange("/key-worker/SYI/allocations", HttpMethod.GET, createHeaderEntity("headers"), String.class)
        def result = jsonSlurper.parseText(allocations.body)

        then:
        response.statusCode == HttpStatus.OK
        jsonSlurper.parseText(response.body) == 10
        confirm.statusCode == HttpStatus.OK
        jsonSlurper.parseText(confirm.body) == 10
        allocations.statusCode == HttpStatus.OK

        // Result list is in the same order as the allocations are done
        result.size() == 11
        result[0].offenderNo == "ALLOCED1" // KW is already 1001
        result[0].allocationType == "M"

        result[1].offenderNo == "UNALLOC1"
        result[1].staffId == 1002 // 1001 not chosen as its no allocated > 0
        result[1].allocationType == "A"
        result[1].assigned.substring(0, 10) == TODAY

        result[2].offenderNo == "UNALLOC2"
        result[2].staffId == 1003

        result[3].offenderNo == "UNALLOC3"
        result[3].staffId == 1001 // Now chosen in staffId numerical order
        // 1001 is not bypassed due to an old allocation because this was NOT auto!

        result[4].offenderNo == "UNALLOC4"
        result[4].staffId == 1003 // 1002 is now full

        result[5].offenderNo == "UNALLOC5"
        result[5].staffId == 1001

        result[6].offenderNo == "UNALLOC6"
        result[6].staffId == 1003

        result[7].offenderNo == "UNALLOC7"
        result[7].staffId == 1001

        result[8].offenderNo == "UNALLOC8"
        result[8].staffId == 1003

        result[9].offenderNo == "UNALLOC9"
        result[9].staffId == 1003 // 1001 is now full

        result[10].offenderNo == "EXPIRED1" // KW set to previous: 1002, despite being full
        result[10].staffId == 1002
        result[10].allocationType == "A"
        result[10].assigned.substring(0, 10) == TODAY
    }

    private void capacityOf1002is1And1001is3() {
        Assert.assertEquals(HttpStatus.OK, restTemplate.exchange("/key-worker/1002/prison/SYI", HttpMethod.POST,
                createHeaderEntity("{\"capacity\": 1, \"status\": \"ACTIVE\"}"), String.class).statusCode)
        Assert.assertEquals(HttpStatus.OK, restTemplate.exchange("/key-worker/1001/prison/SYI", HttpMethod.POST,
                createHeaderEntity("{\"capacity\": 3, \"status\": \"ACTIVE\"}"), String.class).statusCode)
    }
}
