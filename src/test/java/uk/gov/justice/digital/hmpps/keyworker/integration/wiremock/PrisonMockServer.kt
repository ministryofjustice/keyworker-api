package uk.gov.justice.digital.hmpps.keyworker.integration.wiremock

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import org.springframework.http.HttpStatus
import uk.gov.justice.digital.hmpps.keyworker.dto.StaffLocationRoleDto
import uk.gov.justice.digital.hmpps.keyworker.sar.StaffSummary
import uk.gov.justice.digital.hmpps.keyworker.utils.JsonHelper.objectMapper

class PrisonMockServer : WireMockServer(9999) {
  fun stubAllocationHistory(
    prisonId: String,
    json: String,
  ) {
    stubFor(
      WireMock
        .get(WireMock.urlPathEqualTo("/api/key-worker/$prisonId/allocationHistory"))
        .willReturn(
          WireMock
            .aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(200)
            .withBody(json),
        ),
    )
  }

  fun stubOffendersAllocationHistory(json: String) {
    stubFor(
      WireMock
        .post(WireMock.urlPathEqualTo("/api/key-worker/offenders/allocationHistory"))
        .willReturn(
          WireMock
            .aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(200)
            .withBody(json),
        ),
    )
  }

  fun stubAccessCodeListForKeyRole(
    prisonId: String,
    roleCode: String? = "KEY_WORK",
  ) {
    stubFor(
      WireMock
        .get(WireMock.urlPathEqualTo("/api/users/access-roles/caseload/$prisonId/access-role/$roleCode"))
        .willReturn(
          WireMock
            .aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(201)
            .withBody("[]"),
        ),
    )
  }

  fun stubAccessCodeListForKeyAdminRole(
    prisonId: String,
    roleCode: String? = "KW_ADMIN",
  ) {
    stubFor(
      WireMock
        .get(WireMock.urlPathEqualTo("/api/users/access-roles/caseload/$prisonId/access-role/$roleCode"))
        .willReturn(
          WireMock
            .aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(201)
            .withBody("[]"),
        ),
    )
  }

  fun stubAvailableKeyworkersForAutoAllocation(
    prisonId: String,
    json: String,
  ) {
    stubFor(
      WireMock
        .get(WireMock.urlPathEqualTo("/api/key-worker/$prisonId/available"))
        .willReturn(
          WireMock
            .aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(201)
            .withBody(json),
        ),
    )
  }

  fun stubOffendersAtLocationForAutoAllocation(json: String) {
    stubFor(
      WireMock
        .get(WireMock.urlPathEqualTo("/api/bookings/v2"))
        .willReturn(
          WireMock
            .aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(201)
            .withBody(json),
        ),
    )
  }

  fun stubHealthOKResponse() {
    stubFor(
      WireMock
        .get(WireMock.urlPathEqualTo("/health/ping"))
        .willReturn(
          WireMock
            .aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody("""{"status":"UP","HttpStatus":200}"""),
        ),
    )
  }

  fun stubHealthDependencyTimeoutResponse() {
    stubFor(
      WireMock
        .get(WireMock.urlPathEqualTo("/health/ping"))
        .willReturn(
          WireMock
            .aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withFixedDelay(1000)
            .withBody("""{"status":"UP","HttpStatus":200}"""),
        ),
    )
  }

  fun stubKeyworkerRoles(
    prisonId: String,
    staffId: Long,
    json: String,
  ) {
    stubFor(
      WireMock
        .get(WireMock.urlEqualTo("/api/staff/roles/$prisonId/role/KW?staffId=$staffId&activeOnly=false"))
        .willReturn(
          WireMock
            .aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(json),
        ),
    )
  }

  fun stubkeyworkerDetails(
    staffId: Long,
    json: String,
  ) {
    stubFor(
      WireMock
        .get(WireMock.urlEqualTo("/api/staff/$staffId"))
        .willReturn(
          WireMock
            .aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(json),
        ),
    )
  }

  fun stubKeyworkerDetails(
    staffId: Long,
    staffSummary: StaffSummary,
  ) {
    stubFor(
      WireMock
        .get(WireMock.urlEqualTo("/api/staff/$staffId"))
        .willReturn(
          WireMock
            .aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(jacksonObjectMapper().writeValueAsString(staffSummary)),
        ),
    )
  }

  fun stubKeyworkerSearch(
    prisonId: String,
    response: List<StaffLocationRoleDto>,
  ) {
    stubFor(
      WireMock
        .get(WireMock.urlPathEqualTo("/api/staff/roles/$prisonId/role/KW"))
        .willReturn(
          WireMock
            .aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(objectMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubPrisonerStatus(
    offenderNo: String,
    json: String,
  ) {
    stubFor(
      WireMock
        .get(WireMock.urlPathEqualTo("/api/prisoners/$offenderNo"))
        .willReturn(
          WireMock
            .aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(json),
        ),
    )
  }

  fun stubStaffIsKeyworker(
    staffId: String,
    prisonCode: String,
    isKeyworker: Boolean,
    status: HttpStatus = HttpStatus.OK,
  ) {
    stubFor(
      WireMock
        .get(WireMock.urlPathMatching("/api/staff/$staffId/$prisonCode/roles/KW"))
        .willReturn(
          WireMock
            .aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(status.value())
            .withBody(isKeyworker.toString()),
        ),
    )
  }
}
