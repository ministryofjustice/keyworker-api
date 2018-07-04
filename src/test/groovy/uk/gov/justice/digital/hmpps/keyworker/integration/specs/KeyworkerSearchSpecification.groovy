package uk.gov.justice.digital.hmpps.keyworker.integration.specs

import groovy.json.JsonSlurper
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus

class KeyworkerSearchSpecification extends TestSpecification {

    def jsonSlurper = new JsonSlurper()

    def 'keyworker search - decorated with defaults after migration'() {

        given:
        migrated("LEI")
        elite2api.stubKeyworkerSearch("LEI", "User")
        elite2api.stubCaseNoteUsage()

        when:
        def response = restTemplate.exchange("/key-worker/LEI/members?nameFilter=User&statusFilter=ACTIVE",
                HttpMethod.GET, createHeaderEntity("headers"), String.class)

        then:
        response.statusCode == HttpStatus.OK
        def keyworkerList = jsonSlurper.parseText(response.body)
        keyworkerList.size() == 1
        keyworkerList[0].lastName == 'CUser'
        keyworkerList[0].capacity == 6
        keyworkerList[0].numberAllocated == 3
        keyworkerList[0].numKeyWorkerSessions == 4
    }
}



