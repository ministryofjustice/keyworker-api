package uk.gov.justice.digital.hmpps.keyworker.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.test.web.reactive.server.expectBody
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationContext
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationPolicy
import uk.gov.justice.digital.hmpps.keyworker.dto.JobClassificationResponse
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisIdGenerator.newId
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisStaffGenerator.staffRoles
import java.time.LocalDate

class GetStaffJobClassificationsIntegrationTest : IntegrationTest() {
  @Test
  fun `401 unauthorised without a valid token`() {
    webTestClient
      .put()
      .uri(STAFF_JOB_CLASSIFICATION, "NVT", newId())
      .exchange()
      .expectStatus()
      .isUnauthorized
  }

  @Test
  fun `can retrieve all job classification policies for a staff member`() {
    val prisonCode = "AJC"
    val staffId = newId()
    prisonMockServer.stubKeyworkerSearch(prisonCode, staffRoles(listOf(staffId)))
    setContext(AllocationContext.get().copy(policy = AllocationPolicy.PERSONAL_OFFICER))
    givenStaffRole(staffRole(prisonCode, staffId))

    val res =
      getStaffJobClassifications(prisonCode, staffId)
        .expectStatus()
        .isOk
        .expectBody<JobClassificationResponse>()
        .returnResult()
        .responseBody!!

    assertThat(res.policies).containsExactlyInAnyOrderElementsOf(AllocationPolicy.entries)
  }

  @Test
  fun `can retrieve all job classification policies for a keyworker`() {
    val prisonCode = "KJC"
    val staffId = newId()
    prisonMockServer.stubKeyworkerSearch(prisonCode, staffRoles(listOf(staffId)))

    val res =
      getStaffJobClassifications(prisonCode, staffId)
        .expectStatus()
        .isOk
        .expectBody<JobClassificationResponse>()
        .returnResult()
        .responseBody!!

    assertThat(res.policies).containsOnly(AllocationPolicy.KEY_WORKER)
  }

  @Test
  fun `can retrieve all job classification policies for a personal officer`() {
    val prisonCode = "PJC"
    val staffId = newId()
    prisonMockServer.stubKeyworkerSearch(prisonCode, listOf())
    setContext(AllocationContext.get().copy(policy = AllocationPolicy.PERSONAL_OFFICER))
    givenStaffRole(staffRole(prisonCode, staffId))

    val res =
      getStaffJobClassifications(prisonCode, staffId)
        .expectStatus()
        .isOk
        .expectBody<JobClassificationResponse>()
        .returnResult()
        .responseBody!!

    assertThat(res.policies).containsOnly(AllocationPolicy.PERSONAL_OFFICER)
  }

  @Test
  fun `only retrieve active job classification policies for a staff member`() {
    val prisonCode = "AJC"
    val staffId = newId()
    prisonMockServer.stubKeyworkerSearch(prisonCode, staffRoles(listOf(staffId)))
    setContext(AllocationContext.get().copy(policy = AllocationPolicy.PERSONAL_OFFICER))
    givenStaffRole(staffRole(prisonCode, staffId, toDate = LocalDate.now()))

    val res =
      getStaffJobClassifications(prisonCode, staffId)
        .expectStatus()
        .isOk
        .expectBody<JobClassificationResponse>()
        .returnResult()
        .responseBody!!

    assertThat(res.policies).containsOnly(AllocationPolicy.KEY_WORKER)
  }

  private fun getStaffJobClassifications(
    prisonCode: String,
    staffId: Long,
  ) = webTestClient
    .get()
    .uri(STAFF_JOB_CLASSIFICATION, prisonCode, staffId)
    .headers(setHeaders(username = "dps-ui", roles = emptyList()))
    .exchange()

  companion object {
    const val STAFF_JOB_CLASSIFICATION = "/prisons/{prisonCode}/staff/{staffId}/job-classifications"
  }
}
