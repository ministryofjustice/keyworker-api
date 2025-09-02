package uk.gov.justice.digital.hmpps.keyworker.integration.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import org.springframework.http.HttpStatus
import uk.gov.justice.digital.hmpps.keyworker.dto.StaffLocationRoleDto
import uk.gov.justice.digital.hmpps.keyworker.dto.StaffSummary
import uk.gov.justice.digital.hmpps.keyworker.migration.Movement
import uk.gov.justice.digital.hmpps.keyworker.migration.PoHistoricAllocation
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

  fun stubStaffSummaries(staffSummaries: List<StaffSummary>) {
    stubFor(
      WireMock
        .post(WireMock.urlEqualTo("/api/staff"))
        .withRequestBody(equalToJson(objectMapper.writeValueAsString(staffSummaries.map { it.staffId }), true, true))
        .willReturn(
          WireMock
            .aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(objectMapper.writeValueAsString(staffSummaries)),
        ),
    )
  }

  fun stubKeyworkerDetails(
    prisonCode: String,
    staffId: Long,
    staffDetail: StaffLocationRoleDto?,
  ) {
    stubFor(
      WireMock
        .get(WireMock.urlPathMatching("/api/staff/roles/$prisonCode/role/KW"))
        .withQueryParam("staffId", equalTo(staffId.toString()))
        .willReturn(
          WireMock
            .aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(objectMapper.writeValueAsString(listOfNotNull(staffDetail))),
        ),
    )
  }

  fun stubKeyworkerSearch(
    prisonCode: String,
    response: List<StaffLocationRoleDto>,
  ) {
    stubFor(
      WireMock
        .get(WireMock.urlPathEqualTo("/api/staff/roles/$prisonCode/role/KW"))
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

  fun stubIsPrison(
    prisonCode: String,
    flag: Boolean,
  ) {
    stubFor(
      WireMock
        .get(WireMock.urlPathEqualTo("/agencies/$prisonCode"))
        .willReturn(
          WireMock
            .aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(objectMapper.writeValueAsString(flag)),
        ),
    )
  }

  fun stubPoAllocationHistory(
    prisonCode: String,
    response: List<PoHistoricAllocation>,
  ) {
    stubFor(
      WireMock
        .get(WireMock.urlPathEqualTo("/api/personal-officer/$prisonCode/allocation-history"))
        .willReturn(
          WireMock
            .aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(objectMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubGetMovements(
    personIdentifier: String,
    response: List<Movement>,
  ) {
    stubFor(
      WireMock
        .get(WireMock.urlPathEqualTo("/api/movements/offender/$personIdentifier"))
        .withQueryParam("allBookings", equalTo("true"))
        .willReturn(
          WireMock
            .aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(objectMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubStaffEmail(
    staffId: Long,
    staffEmail: String?,
  ) {
    stubFor(
      WireMock
        .get(WireMock.urlPathEqualTo("/api/staff/$staffId/emails"))
        .willReturn(
          WireMock
            .aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(if (staffEmail == null) HttpStatus.NO_CONTENT.value() else HttpStatus.OK.value())
            .withBody(objectMapper.writeValueAsString(setOfNotNull(staffEmail))),
        ),
    )
  }
}
