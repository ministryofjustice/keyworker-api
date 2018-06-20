package uk.gov.justice.digital.hmpps.keyworker.integration.specs

import groovy.json.JsonSlurper
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

class UpdateStatusSpecification extends TestSpecification {

    def jsonSlurper = new JsonSlurper()
    def formatter = DateTimeFormatter.ofPattern("YYYY-MM-dd")
    def tomorrow = LocalDate.now().plus(1, ChronoUnit.DAYS).format(formatter)
    def keyworkerBackFromAnnualLeave= jsonSlurper.parseText("{\"status\" : \"UNAVAILABLE_ANNUAL_LEAVE\", \"activeDate\": \"2018-06-06\", \"behaviour\" : \"KEEP_ALLOCATIONS_NO_AUTO\", \"capacity\": \"6\"}");
    def keyworkerOnAnnualLeave= jsonSlurper.parseText("{\"status\" : \"UNAVAILABLE_ANNUAL_LEAVE\", \"activeDate\": \""+tomorrow+"\", \"behaviour\" : \"KEEP_ALLOCATIONS_NO_AUTO\", \"capacity\": \"6\"}");
    def keyworkerActive= jsonSlurper.parseText("{\"status\" : \"UNAVAILABLE_ANNUAL_LEAVE\", \"capacity\": \"6\"}");
    def keyworkerInactive= jsonSlurper.parseText("{\"status\" : \"INACTIVE\", \"capacity\": \"6\"}");


    def 'Update status service successfully changes the status of a keyworker returning from annual leave'() {

        given:
        migrated("LEI")
        elite2api.stubAvailableKeyworkersForStatusUpdate("LEI")
        restTemplate.exchange("/key-worker/-15/prison/LEI", HttpMethod.POST, createHeaderEntity(keyworkerBackFromAnnualLeave), String.class);
        restTemplate.exchange("/key-worker/-13/prison/LEI", HttpMethod.POST, createHeaderEntity(keyworkerBackFromAnnualLeave), String.class);
        restTemplate.exchange("/key-worker/-12/prison/LEI", HttpMethod.POST, createHeaderEntity(keyworkerOnAnnualLeave), String.class);
        restTemplate.exchange("/key-worker/-11/prison/LEI", HttpMethod.POST, createHeaderEntity(keyworkerActive), String.class);
        restTemplate.exchange("/key-worker/-14/prison/LEI", HttpMethod.POST, createHeaderEntity(keyworkerInactive), String.class);


        when:
        def response = restTemplate.exchange("/key-worker/batch/update-status", HttpMethod.POST, createHeaderEntityForAdminUser("headers"), String.class)

        then:
        response.statusCode == HttpStatus.OK
        def listOfUpdatedKeyworkerIds = jsonSlurper.parseText(response.body)
        listOfUpdatedKeyworkerIds.size() == 2
        listOfUpdatedKeyworkerIds.contains(-15)
        listOfUpdatedKeyworkerIds.contains(-13)
    }
}



