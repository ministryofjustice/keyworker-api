package uk.gov.justice.digital.hmpps.keyworker.integration.mockApis

import com.github.tomakehurst.wiremock.junit.WireMockRule
import groovy.json.JsonOutput
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.util.UriTemplate
import uk.gov.justice.digital.hmpps.keyworker.dto.Page
import uk.gov.justice.digital.hmpps.keyworker.integration.mockResponses.*
import uk.gov.justice.digital.hmpps.keyworker.rolemigration.remote.RemoteRoleService

import static com.github.tomakehurst.wiremock.client.WireMock.*
import static uk.gov.justice.digital.hmpps.keyworker.services.NomisService.*

class Elite2Api extends WireMockRule {

    public static final int WIREMOCK_PORT = 8999

    public static String NOMIS_API_PREFIX = "/api"


    Elite2Api() {
        super(WIREMOCK_PORT)
    }

    void stubAllocationHistory(String prisonId) {
        stubFor(get(urlEqualTo(new UriTemplate(NOMIS_API_PREFIX + URI_KEY_WORKER_GET_ALLOCATION_HISTORY).expand(prisonId).toString()))
                .willReturn(aResponse().withStatus(HttpStatus.OK.value())
                .withBody(OffenderKeyworkerDtoListStub.getResponse(prisonId))
                .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)))
    }

    void stubAllocationHistoryForAutoAllocation(String prisonId) {
        stubFor(get(urlEqualTo(new UriTemplate(NOMIS_API_PREFIX + URI_KEY_WORKER_GET_ALLOCATION_HISTORY).expand(prisonId).toString()))
                .willReturn(aResponse().withStatus(HttpStatus.OK.value())
                .withBody(OffenderKeyworkerDtoListStub.getResponseForAutoAllocation(prisonId))
                .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)))
    }

    def stubAvailableKeyworkers = { prisonId ->
        stubFor(get(urlEqualTo(new UriTemplate(NOMIS_API_PREFIX + URI_AVAILABLE_KEYWORKERS).expand(prisonId).toString()))
                .willReturn(aResponse().withStatus(HttpStatus.OK.value())
                .withBody(KeyworkerDtoListStub.getResponse())
                .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)))
    }

    def stubAvailableKeyworkersForStatusUpdate = { prisonId ->
        stubFor(get(urlEqualTo(new UriTemplate(NOMIS_API_PREFIX + URI_AVAILABLE_KEYWORKERS).expand(prisonId).toString()))
                .willReturn(aResponse().withStatus(HttpStatus.OK.value())
                .withBody(KeyworkerDtoListStub.getResponseForStatusUpdate())
                .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)))
    }

    def stubAvailableKeyworkersForAutoAllocation = { prisonId ->
        stubFor(get(urlEqualTo(new UriTemplate(NOMIS_API_PREFIX + URI_AVAILABLE_KEYWORKERS).expand(prisonId).toString()))
                .willReturn(aResponse().withStatus(HttpStatus.OK.value())
                .withBody(KeyworkerDtoListStub.getResponseForAutoAllocation())
                .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)))
    }

    def stubOffendersAtLocationForAutoAllocation = { String prisonId ->
        stubFor(get(urlEqualTo(new UriTemplate(NOMIS_API_PREFIX + URI_ACTIVE_OFFENDERS_BY_AGENCY).expand(prisonId).toString()))
                .willReturn(aResponse().withStatus(HttpStatus.OK.value())
                .withBody(OffenderLocationDtoListStub.getResponseForAutoAllocation(prisonId))
                .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)))
    }

    def stubKeyworkerSearch = { String prisonId, String nameFilterPattern ->
        stubFor(get(urlPathMatching(new UriTemplate(NOMIS_API_PREFIX + GET_STAFF_IN_SPECIFIC_PRISON).expand(prisonId).toString()))
                .withQueryParam('nameFilter', matching(nameFilterPattern))
                .willReturn(aResponse().withStatus(HttpStatus.OK.value())
                .withBody(StaffLocationRoleDtoListStub.getResponse())
                .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .withHeader(Page.HEADER_PAGE_LIMIT, '50')
                .withHeader(Page.HEADER_PAGE_OFFSET, '0')
                .withHeader(Page.HEADER_TOTAL_RECORDS, '4')
        ))
    }

    def stubCaseNoteUsage = {
        stubFor(post(urlPathMatching(new UriTemplate(NOMIS_API_PREFIX + CASE_NOTE_USAGE).expand().toString()))
                .willReturn(aResponse().withStatus(HttpStatus.OK.value())
                .withBody(CaseNoteUsageListStub.getResponse())
                .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
        ))
    }

    void stubCaseNoteUsageFor(int staffId, String type, String fromDate, String toDate, def response) {

        def body = [staffIds: [staffId], type: type, fromDate: fromDate, toDate: toDate]

        stubFor(post(urlPathMatching(new UriTemplate(NOMIS_API_PREFIX + CASE_NOTE_USAGE).expand().toString()))
                .withRequestBody(equalTo(JsonOutput.toJson(body)))
                .willReturn(aResponse().withStatus(HttpStatus.OK.value())
                .withBody(JsonOutput.toJson(response))
                .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
        ))
    }

    void stubCaseNoteUsagePrisonerFor(List<String> offendersNos, Integer staffId, String type, String fromDate, String toDate, def response) {

        def body = [offenderNos: offendersNos, staffId: staffId, type: type, fromDate: fromDate, toDate: toDate]

        stubFor(post(urlPathMatching(new UriTemplate(NOMIS_API_PREFIX + CASE_NOTE_USAGE_FOR_PRISONERS).expand().toString()))
                .withRequestBody(equalTo(JsonOutput.toJson(body)))
                .willReturn(aResponse().withStatus(HttpStatus.OK.value())
                .withBody(JsonOutput.toJson(response))
                .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
        ))
    }

    void stubAccessCodeListForKeyRole(String prisonId) {
        stubFor(get(urlEqualTo(new UriTemplate(NOMIS_API_PREFIX+ RemoteRoleService.STAFF_ACCESS_CODES_LIST_URL).expand(prisonId, "KEY_WORK").toString()))
                .willReturn(aResponse().withStatus(HttpStatus.OK.value())
                .withBody("[]")
                .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)))
    }

    def stubAccessCodeListForKeyAdminRole(String prisonId) {
        stubFor(get(urlEqualTo(new UriTemplate(NOMIS_API_PREFIX+ RemoteRoleService.STAFF_ACCESS_CODES_LIST_URL).expand(prisonId, "KW_ADMIN").toString()))
                .willReturn(aResponse().withStatus(HttpStatus.OK.value())
                .withBody("[]")
                .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)))
    }
    void stubKeyworkerDetails(String prisonId, Long staffId) {
        stubFor(get(urlEqualTo(new UriTemplate(NOMIS_API_PREFIX+ GET_STAFF_IN_SPECIFIC_PRISON +"?staffId={staffId}&activeOnly=false").expand(prisonId, staffId).toString()))
                .willReturn(aResponse().withStatus(HttpStatus.OK.value())
                .withBody(StaffLocationRoleDtoListStub.response)
                .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)))
    }

    void stubKeyworkerDetails_basicDetailsOnly(Long staffId) {
        stubFor(get(urlEqualTo(new UriTemplate(NOMIS_API_PREFIX+ URI_STAFF).expand(staffId).toString()))
                .willReturn(aResponse().withStatus(HttpStatus.OK.value())
                .withBody(StaffLocationRoleDtoStub.response) //empty list
                .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)))
    }

    void stubKeyworkerDetails_emptyListResponse(String prisonId, Long staffId) {
        stubFor(get(urlEqualTo(new UriTemplate(NOMIS_API_PREFIX+ GET_STAFF_IN_SPECIFIC_PRISON +"?staffId={staffId}&activeOnly=false").expand(prisonId, staffId).toString()))
                .willReturn(aResponse().withStatus(HttpStatus.OK.value())
                .withBody("[]") //empty list
                .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)))
    }

    void stubHealthOKResponse() {
        stubFor(get(urlEqualTo("/health"))
                .willReturn(aResponse().withStatus(HttpStatus.OK.value())
                .withBody("""{"status":"UP","HttpStatus":200}""")
                .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)))
    }

    void stubHealthElite2DownResponse() {
        stubFor(get(urlEqualTo("/health"))
                .willReturn(aResponse().withStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .withBody("""{"status":"DOWN","HttpStatus":503}""")
                .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)))
    }

    void stubOffenderLookup(String prisonId, String offenderNo) {
        stubFor(get(urlEqualTo(new UriTemplate(NOMIS_API_PREFIX+ URI_ACTIVE_OFFENDER_BY_AGENCY).expand(prisonId, offenderNo).toString()))
                .willReturn(aResponse().withStatus(HttpStatus.OK.value())
                .withBody(OffenderLocationDtoListStub.getResponseOffender(prisonId, offenderNo))
                .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)))
    }

    void stubStaffUserDetails(String username) {
        if (username.equals("omicadmin")) {
            stubFor(get(urlEqualTo(new UriTemplate(NOMIS_API_PREFIX + GET_USER_DETAILS).expand(username).toString()))
                    .willReturn(aResponse().withStatus(HttpStatus.NOT_FOUND.value())
                    .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)))
        } else {
            stubFor(get(urlEqualTo(new UriTemplate(NOMIS_API_PREFIX + GET_USER_DETAILS).expand(username).toString()))
                    .willReturn(aResponse().withStatus(HttpStatus.OK.value())
                    .withBody(StaffUserStub.responseItag(username)) //empty list
                    .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)))
        }
    }

    void stubPrisonerLookup(String offenderNo) {
        stubFor(get(urlEqualTo(new UriTemplate(NOMIS_API_PREFIX+ URI_PRISONER_LOOKUP).expand(offenderNo).toString()))
                .willReturn(aResponse().withStatus(HttpStatus.OK.value())
                .withBody(OffenderLocationDtoListStub.getResponsePrisoner(offenderNo))
                .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)))
    }

    void stubOffenderAllocationHistory(String offenderNo) {
        stubFor(post(urlPathMatching(new UriTemplate(NOMIS_API_PREFIX + URI_OFFENDERS_ALLOCATION_HISTORY).expand().toString()))
                .willReturn(aResponse().withStatus(HttpStatus.OK.value())
                .withBody(OffenderKeyworkerDtoListStub.getResponseAllocationHistory(offenderNo))
                .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
        ))
    }
}
