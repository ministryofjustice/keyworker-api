package uk.gov.justice.digital.hmpps.keyworker.integration.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import org.springframework.http.HttpStatus
import uk.gov.justice.digital.hmpps.keyworker.dto.StaffSearchRequest
import uk.gov.justice.digital.hmpps.keyworker.dto.StaffStatus
import uk.gov.justice.digital.hmpps.keyworker.integration.nomisuserroles.NomisStaffMembers
import uk.gov.justice.digital.hmpps.keyworker.integration.nomisuserroles.StaffJobClassification
import uk.gov.justice.digital.hmpps.keyworker.integration.nomisuserroles.StaffJobClassificationRequest
import uk.gov.justice.digital.hmpps.keyworker.utils.JsonHelper.objectMapper

class NomisUserRolesMockServer : WireMockServer(9994) {
  fun stubSetStaffRole(sjc: StaffJobClassification) {
    stubFor(
      WireMock
        .put(WireMock.urlPathEqualTo("/agency/${sjc.prisonCode}/staff-members/${sjc.staffId}/staff-role/KW"))
        .withRequestBody(
          equalToJson(
            objectMapper.writeValueAsString(sjc.asRequest()),
            true,
            true,
          ),
        ).willReturn(
          WireMock
            .aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(objectMapper.writeValueAsString(sjc)),
        ),
    )
  }

  fun stubGetAllStaff(
    prisonCode: String,
    response: NomisStaffMembers,
  ) = stubGetUserStaff(prisonCode, response, StaffSearchRequest(null, StaffStatus.ACTIVE, null))

  fun stubGetUserStaff(
    prisonCode: String,
    response: NomisStaffMembers,
    ssr: StaffSearchRequest? = null,
  ) {
    val request = WireMock.get(WireMock.urlPathEqualTo("/users"))
    if (ssr?.query?.isNotBlank() == true) {
      request.withQueryParam("nameFilter", equalTo(ssr.query))
    }
    request.withQueryParam("status", equalTo("ACTIVE"))
    request.withQueryParam("caseload", equalTo(prisonCode))
    stubFor(
      request.willReturn(
        WireMock
          .aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(objectMapper.writeValueAsString(response)),
      ),
    )
  }
}

fun StaffJobClassification.asRequest() = StaffJobClassificationRequest(position, scheduleType, hoursPerWeek, fromDate, toDate)
