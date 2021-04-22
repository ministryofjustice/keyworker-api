package uk.gov.justice.digital.hmpps.keyworker.integration.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.matching
import jodd.net.HttpStatus
import uk.gov.justice.digital.hmpps.keyworker.dto.Page

class EliteMockServer : WireMockServer(9999) {
  fun stubAllocationHistory(prisonId: String, json: String) {
    stubFor(
      WireMock.get(WireMock.urlPathEqualTo("/api/key-worker/$prisonId/allocationHistory"))
        .willReturn(
          WireMock.aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(200)
            .withBody(json)
        )
    )
  }

  fun stubOffendersAllocationHistory(json: String) {
    stubFor(
      WireMock.post(WireMock.urlPathEqualTo("/api/key-worker/offenders/allocationHistory"))
        .willReturn(
          WireMock.aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(200)
            .withBody(json)
        )
    )
  }

  fun stubAccessCodeListForKeyRole(prisonId: String, roleCode: String? = "KEY_WORK") {
    stubFor(
      WireMock.get(WireMock.urlPathEqualTo("/api/users/access-roles/caseload/$prisonId/access-role/$roleCode"))
        .willReturn(
          WireMock.aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(201)
            .withBody("[]")
        )
    )
  }

  fun stubAccessCodeListForKeyAdminRole(prisonId: String, roleCode: String? = "KW_ADMIN") {
    stubFor(
      WireMock.get(WireMock.urlPathEqualTo("/api/users/access-roles/caseload/$prisonId/access-role/$roleCode"))
        .willReturn(
          WireMock.aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(201)
            .withBody("[]")
        )
    )
  }

  fun stubAvailableKeyworkersForAutoAllocation(prisonId: String, json: String) {
    stubFor(
      WireMock.get(WireMock.urlPathEqualTo("/api/key-worker/$prisonId/available"))
        .willReturn(
          WireMock.aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(201)
            .withBody(json)
        )
    )
  }

  fun stubOffendersAtLocationForAutoAllocation(json: String) {
    stubFor(
      WireMock.get(WireMock.urlPathEqualTo("/api/bookings"))
        .willReturn(
          WireMock.aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(201)
            .withBody(json)
        )
    )
  }

  fun stubHealthOKResponse() {
    stubFor(
      WireMock.get(WireMock.urlPathEqualTo("/health/ping"))
        .willReturn(
          WireMock.aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.ok().status())
            .withBody("""{"status":"UP","HttpStatus":200}""")
        )
    )
  }

  fun stubHealthDependencyTimeoutResponse() {
    stubFor(
      WireMock.get(WireMock.urlPathEqualTo("/health/ping"))
        .willReturn(
          WireMock.aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.ok().status()).withFixedDelay(1000)
            .withBody("""{"status":"UP","HttpStatus":200}""")
        )
    )
  }

  fun stubKeyworkerRoles(prisonId: String, staffId: Long, json: String) {
    stubFor(
      WireMock.get(WireMock.urlEqualTo("/api/staff/roles/$prisonId/role/KW?staffId=$staffId&activeOnly=false"))
        .willReturn(
          WireMock.aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.ok().status())
            .withBody(json)
        )
    )
  }

  fun stubkeyworkerDetails(staffId: Long, json: String) {
    stubFor(
      WireMock.get(WireMock.urlEqualTo("/api/staff/$staffId"))
        .willReturn(
          WireMock.aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.ok().status())
            .withBody(json)
        )
    )
  }

  fun stubKeyworkerSearch(prisonId: String, username: String, json: String, totalRecords: Int? = 1, status: String? = "ACTIVE") {
    stubFor(
      WireMock.get(WireMock.urlPathEqualTo("/api/staff/roles/$prisonId/role/KW"))
        .withQueryParam("nameFilter", matching(username))
        .withQueryParam("statusFilter", matching(status))
        .willReturn(
          WireMock.aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.ok().status())
            .withBody(json)
            .withHeader(Page.HEADER_PAGE_LIMIT, "50")
            .withHeader(Page.HEADER_PAGE_OFFSET, "0")
            .withHeader(Page.HEADER_TOTAL_RECORDS, totalRecords.toString())
        )
    )
  }

  fun stubCaseNoteUsage(json: String) {
    stubFor(
      WireMock.get(WireMock.urlPathEqualTo("/api/case-notes/staff-usage"))
        .willReturn(
          WireMock.aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.ok().status())
            .withBody(json)
        )
    )
  }

  fun stubPrisonerStatus(offenderNo: String, json: String) {
    stubFor(
      WireMock.get(WireMock.urlPathEqualTo("/api/prisoners/$offenderNo"))
        .willReturn(
          WireMock.aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.ok().status())
            .withBody(json)
        )
    )
  }
}
