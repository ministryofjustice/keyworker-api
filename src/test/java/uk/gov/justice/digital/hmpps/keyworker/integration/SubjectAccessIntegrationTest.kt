package uk.gov.justice.digital.hmpps.keyworker.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.test.web.reactive.server.expectBody
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationContext
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationPolicy
import uk.gov.justice.digital.hmpps.keyworker.dto.CodedDescription
import uk.gov.justice.digital.hmpps.keyworker.dto.DeallocationReason
import uk.gov.justice.digital.hmpps.keyworker.dto.staff.StaffSummary
import uk.gov.justice.digital.hmpps.keyworker.sar.SarAllocation
import uk.gov.justice.digital.hmpps.keyworker.sar.StaffMember
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisIdGenerator.newId
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisIdGenerator.personIdentifier
import java.time.LocalDate
import java.time.LocalDateTime

class SubjectAccessIntegrationTest : IntegrationTest() {
  @Test
  fun `401 unauthorised without a valid token`() {
    webTestClient
      .get()
      .uri(SAR_URL)
      .exchange()
      .expectStatus()
      .isUnauthorized
  }

  @Test
  fun `403 forbidden without correct role`() {
    retrieveSar(personIdentifier(), role = "ROLE_ANY__OTHER_RW").expectStatus().isForbidden
  }

  @Test
  fun `400 - no prn or crn set`() {
    retrieveSar(null, crn = null).expectStatus().isEqualTo(400)
  }

  @Test
  fun `209 - service does not support crn`() {
    retrieveSar(null, crn = "A123456").expectStatus().isEqualTo(209)
  }

  @Test
  fun `204 no content - no allocation information`() {
    retrieveSar(personIdentifier()).expectStatus().isNoContent
  }

  @Test
  fun `200 ok - allocations returned for all policies`() {
    val prisonCode = "SA1"
    val pi = personIdentifier()
    setContext(AllocationContext.get().copy(policy = AllocationPolicy.KEY_WORKER))
    val al1 = givenAllocation(staffAllocation(pi, prisonCode))
    setContext(AllocationContext.get().copy(policy = AllocationPolicy.PERSONAL_OFFICER))
    val al2 = givenAllocation(staffAllocation(pi, prisonCode))
    val staffMembers =
      listOf(
        staffSummary("Key", "Worker", al1.staffId),
        staffSummary("Personal", "Officer", al2.staffId),
      )
    prisonMockServer.stubStaffSummaries(staffMembers)

    val res =
      retrieveSar(pi)
        .expectStatus()
        .isOk
        .expectBody<TestSarContent>()
        .returnResult()
        .responseBody!!

    val sarAllocations = res.content

    assertThat(sarAllocations.map { it.policy }).containsExactlyInAnyOrder(
      CodedDescription("PERSONAL_OFFICER", "Personal officer"),
      CodedDescription("KEY_WORKER", "Key worker"),
    )
    assertThat(sarAllocations.map { it.staffMember }).containsExactlyInAnyOrderElementsOf(
      staffMembers.map { StaffMember(it.firstName, it.lastName) },
    )
  }

  @Test
  fun `200 ok - allocations restricted to date range`() {
    val prisonCode = "SA2"
    val pi = personIdentifier()
    val staffId = newId()
    val from = LocalDate.now().minusDays(20)
    val to = LocalDate.now().minusDays(10)
    givenAllocation(
      staffAllocation(
        pi,
        prisonCode,
        staffId,
        LocalDateTime.now().minusDays(30),
        active = false,
        deallocatedAt = LocalDateTime.now().minusDays(20),
        deallocatedBy = "D341L",
        deallocationReason = DeallocationReason.OVERRIDE,
      ),
    )
    givenAllocation(
      staffAllocation(
        pi,
        prisonCode,
        staffId,
        LocalDateTime.now().minusDays(20),
        active = false,
        deallocatedAt = LocalDateTime.now().minusDays(15),
        deallocatedBy = "D341L",
        deallocationReason = DeallocationReason.OVERRIDE,
      ),
    )
    givenAllocation(
      staffAllocation(
        pi,
        prisonCode,
        staffId,
        LocalDateTime.now().minusDays(15),
        active = false,
        deallocatedAt = LocalDateTime.now().minusDays(10),
        deallocatedBy = "D341L",
        deallocationReason = DeallocationReason.OVERRIDE,
      ),
    )
    givenAllocation(
      staffAllocation(
        pi,
        prisonCode,
        staffId,
        LocalDateTime.now().minusDays(10),
        active = false,
        deallocatedAt = LocalDateTime.now().minusDays(5),
        deallocatedBy = "D341L",
        deallocationReason = DeallocationReason.OVERRIDE,
      ),
    )
    givenAllocation(staffAllocation(pi, prisonCode, staffId, LocalDateTime.now().minusDays(5)))
    val staffMember = staffSummary("First", "Last", staffId)
    prisonMockServer.stubStaffSummaries(listOf(staffMember))

    val res =
      retrieveSar(pi, null, from, to)
        .expectStatus()
        .isOk
        .expectBody<TestSarContent>()
        .returnResult()
        .responseBody!!

    val sarAllocations = res.content

    assertThat(sarAllocations).hasSize(3)
    assertThat(
      sarAllocations.all {
        it.allocatedAt.isAfter(from.atStartOfDay()) ||
          it.allocatedAt.isBefore(to.plusDays(1).atStartOfDay())
      },
    ).isTrue()
    assertThat(sarAllocations.map { it.policy }.distinct()).containsOnly(CodedDescription("KEY_WORKER", "Key worker"))
    assertThat(sarAllocations.map { it.staffMember }.distinct())
      .containsOnly(StaffMember(staffMember.firstName, staffMember.lastName))
  }

  private fun retrieveSar(
    prn: String?,
    crn: String? = null,
    from: LocalDate? = null,
    to: LocalDate? = null,
    role: String? = "ROLE_SAR_DATA_ACCESS",
  ) = webTestClient
    .get()
    .uri { b ->
      b.path(SAR_URL)
      prn?.also { b.queryParam("prn", it) }
      crn?.also { b.queryParam("crn", it) }
      from?.also { b.queryParam("fromDate", it) }
      to?.also { b.queryParam("toDate", it) }
      b.build()
    }.headers(setHeaders(roles = listOfNotNull(role)))
    .exchange()

  fun staffSummary(
    firstName: String,
    lastName: String,
    id: Long = newId(),
  ): StaffSummary = StaffSummary(id, firstName, lastName)

  companion object {
    const val SAR_URL = "/subject-access-request"
  }

  private data class TestSarContent(
    val content: List<SarAllocation>,
  )
}
