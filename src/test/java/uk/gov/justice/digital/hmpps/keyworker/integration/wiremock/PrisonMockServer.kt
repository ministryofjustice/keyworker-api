package uk.gov.justice.digital.hmpps.keyworker.integration.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import org.springframework.http.HttpStatus
import uk.gov.justice.digital.hmpps.keyworker.dto.NomisStaffRole
import uk.gov.justice.digital.hmpps.keyworker.dto.StaffSummary
import uk.gov.justice.digital.hmpps.keyworker.migration.Movement
import uk.gov.justice.digital.hmpps.keyworker.migration.PoHistoricAllocation
import uk.gov.justice.digital.hmpps.keyworker.utils.JsonHelper.objectMapper

class PrisonMockServer : WireMockServer(9999) {
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
    staffDetail: NomisStaffRole?,
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
    response: List<NomisStaffRole>,
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

  fun stubKeyworkerSearchNotFound(prisonCode: String) {
    stubFor(
      WireMock
        .get(WireMock.urlPathEqualTo("/api/staff/roles/$prisonCode/role/KW"))
        .willReturn(
          WireMock
            .aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.NOT_FOUND.value()),
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
