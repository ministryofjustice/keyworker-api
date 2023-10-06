package uk.gov.justice.digital.hmpps.keyworker.controllers

import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.HttpHeaders
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import uk.gov.justice.digital.hmpps.keyworker.dto.KeyworkerAllocationDetailsDto
import uk.gov.justice.digital.hmpps.keyworker.dto.OffenderKeyworkerDto
import uk.gov.justice.digital.hmpps.keyworker.dto.Page
import uk.gov.justice.digital.hmpps.keyworker.services.KeyworkerService

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class KeyworkerRoleServiceControllerTest() {

  @Autowired
  lateinit var mockMvc: MockMvc

  @MockBean
  private lateinit var keyworkerService: KeyworkerService

  @Test
  @WithMockUser(roles = ["MAINTAIN_KEYWORKERS"])
  fun `getKeyworkerAllocations should return OK for users with ROLE_MAINTAIN_KEYWORKERS role`() {
    val prisonId = "123"
    val staffId = "321"
    val offenderNo = "654321"
    val page = Page<KeyworkerAllocationDetailsDto>(emptyList(), defaultHttpHeaders())
    whenever(keyworkerService.getAllocations(any(), any())).thenReturn(page)

    val offenderNos = listOf("OFFENDER1", "OFFENDER2")

    val offenderKeyworkerDetails = listOf(
      OffenderKeyworkerDto()
    )
    `when`(keyworkerService.getOffenderKeyworkerDetailList(anyString(), any())).thenReturn(offenderKeyworkerDetails)

    mockMvc.perform(get("/key-worker/{prisonId}/allocations", prisonId))
      .andExpect(status().isOk)
    mockMvc.perform(get("/key-worker/{prisonId}/available", prisonId))
      .andExpect(status().isOk)
    mockMvc.perform(get("/key-worker/{prisonId}/offenders", prisonId))
      .andExpect(status().isOk)
    mockMvc.perform(get("/key-worker/{prisonId}/offenders/unallocated", prisonId))
      .andExpect(status().isOk)
  }

  @Test
  @WithMockUser(roles = ["OTHER_ROLE"])
  fun `getKeyworkerAllocations should return OK for users with OTHER_ROLE role`() {
    val prisonId = "123"
    val page = Page<KeyworkerAllocationDetailsDto>(emptyList(), defaultHttpHeaders())
    whenever(keyworkerService.getAllocations(any(), any())).thenReturn(page)
    mockMvc.perform(get("/key-worker/{prisonId}/allocations", prisonId))
      .andExpect(status().isForbidden)
    mockMvc.perform(get("/key-worker/{prisonId}/available", prisonId))
      .andExpect(status().isForbidden)
  }

  private fun defaultHttpHeaders(): HttpHeaders {
    val httpHeaders = HttpHeaders()
    httpHeaders.set(Page.HEADER_TOTAL_RECORDS, "0")
    httpHeaders.set(Page.HEADER_PAGE_OFFSET, "0")
    httpHeaders.set(Page.HEADER_PAGE_LIMIT, "0")
    return httpHeaders
  }
}
