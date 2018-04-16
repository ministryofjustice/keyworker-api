package uk.gov.justice.digital.hmpps.keyworker.integration.mockApis

import com.github.tomakehurst.wiremock.junit.WireMockRule
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.util.UriTemplate
import uk.gov.justice.digital.hmpps.keyworker.integration.mockResponses.OffenderKeyworkerDtoListStub
import uk.gov.justice.digital.hmpps.keyworker.integration.mockResponses.KeyworkerDtoListStub
import uk.gov.justice.digital.hmpps.keyworker.integration.mockResponses.StaffLocationRoleDtoListStub
import uk.gov.justice.digital.hmpps.keyworker.integration.mockResponses.StaffLocationRoleDtoStub
import uk.gov.justice.digital.hmpps.keyworker.rolemigration.remote.RemoteRoleService

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse
import static com.github.tomakehurst.wiremock.client.WireMock.get
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
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
                .withBody(OffenderKeyworkerDtoListStub.response)
                .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)))
    }
    void stubAvailableKeyworkers(String prisonId) {
        stubFor(get(urlEqualTo(new UriTemplate(NOMIS_API_PREFIX+ URI_AVAILABLE_KEYWORKERS).expand(prisonId).toString()))
                .willReturn(aResponse().withStatus(HttpStatus.OK.value())
                .withBody(StaffLocationRoleDtoListStub.response)
                .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)))
    }
    void stubAccessCodeListForKeyRole(String prisonId) {
        stubFor(get(urlEqualTo(new UriTemplate(NOMIS_API_PREFIX+ RemoteRoleService.STAFF_ACCESS_CODES_LIST_URL).expand(prisonId, "KEY_ROLE").toString()))
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
        stubFor(get(urlEqualTo(new UriTemplate(NOMIS_API_PREFIX+ GET_STAFF_IN_SPECIFIC_PRISON +"?staffId={staffId}").expand(prisonId, staffId).toString()))
                .willReturn(aResponse().withStatus(HttpStatus.OK.value())
                .withBody(KeyworkerDtoListStub.response)
                .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)))
    }

    void stubKeyworkerDetails_basicDetailsOnly(Long staffId) {
        stubFor(get(urlEqualTo(new UriTemplate(NOMIS_API_PREFIX+ URI_STAFF).expand(staffId).toString()))
                .willReturn(aResponse().withStatus(HttpStatus.OK.value())
                .withBody(StaffLocationRoleDtoStub.response) //empty list
                .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)))
    }

    void stubKeyworkerDetails_emptyListResponse(String prisonId, Long staffId) {
        stubFor(get(urlEqualTo(new UriTemplate(NOMIS_API_PREFIX+ GET_STAFF_IN_SPECIFIC_PRISON +"?staffId={staffId}").expand(prisonId, staffId).toString()))
                .willReturn(aResponse().withStatus(HttpStatus.OK.value())
                .withBody("[]") //empty list
                .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)))
    }

}
