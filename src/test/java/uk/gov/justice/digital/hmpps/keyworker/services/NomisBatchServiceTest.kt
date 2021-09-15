package uk.gov.justice.digital.hmpps.keyworker.services

import com.microsoft.applicationinsights.TelemetryClient
import com.nhaarman.mockitokotlin2.whenever
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.ArgumentMatchers.isA
import org.mockito.ArgumentMatchers.isNull
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit.jupiter.SpringExtension
import uk.gov.justice.digital.hmpps.keyworker.config.RetryConfiguration
import uk.gov.justice.digital.hmpps.keyworker.dto.CaseloadUpdate
import uk.gov.justice.digital.hmpps.keyworker.dto.Prison

@ExtendWith(SpringExtension::class)
@ContextConfiguration(classes = [NomisBatchService::class, RetryConfiguration::class])
class NomisBatchServiceTest {
  @Autowired
  private lateinit var batchService: NomisBatchService

  @MockBean
  private lateinit var nomisService: NomisService

  @MockBean
  private lateinit var telemetryClient: TelemetryClient

  @Test
  fun enableNomis_makesPrisonApiCalls() {
    val prisons = listOf(MDI, LEI, LPI)
    whenever(nomisService.allPrisons).thenReturn(prisons)
    val MDIResponse = CaseloadUpdate.builder().caseload(MDI.prisonId).numUsersEnabled(2).build()
    whenever(nomisService.enableNewNomisForCaseload(eq(MDI.prisonId))).thenReturn(MDIResponse)
    val LEIResponse = CaseloadUpdate.builder().caseload(LEI.prisonId).numUsersEnabled(0).build()
    whenever(nomisService.enableNewNomisForCaseload(eq(LEI.prisonId))).thenReturn(LEIResponse)
    val LPIResponse = CaseloadUpdate.builder().caseload(LPI.prisonId).numUsersEnabled(14).build()
    whenever(nomisService.enableNewNomisForCaseload(eq(LPI.prisonId))).thenReturn(LPIResponse)

    batchService.enableNomis()

    verify(nomisService).allPrisons
    verify(nomisService).enableNewNomisForCaseload(eq(MDI.prisonId))
    verify(nomisService).enableNewNomisForCaseload(eq(LEI.prisonId))
    verify(nomisService).enableNewNomisForCaseload(eq(LPI.prisonId))
    verify(telemetryClient, times(3)).trackEvent(
      eq("ApiUsersEnabled"),
      isA(
        Map::class.java
      ) as MutableMap<String, String>?,
      isNull()
    )
  }

  @Test
  fun testEnabledNewNomisCamelRoute_NoOpOnGetAllPrisonsError() {
    whenever(nomisService.allPrisons).thenThrow(RuntimeException("Error"))

    batchService.enableNomis()

    verify(nomisService).allPrisons
    verify(nomisService, never()).enableNewNomisForCaseload(anyString())
    verify(telemetryClient, times(0)).trackEvent(
      anyString(),
      any(
        Map::class.java
      ) as MutableMap<String, String>?,
      isNull()
    )
  }

  @Test
  fun testEnabledNewNomisCamelRoute_RetriesOnEnablePrisonsError() {
    val prisons = listOf(MDI)
    whenever(nomisService.allPrisons).thenReturn(prisons)
    val MDIResponse = CaseloadUpdate.builder().caseload(MDI.prisonId).numUsersEnabled(2).build()
    whenever(nomisService.enableNewNomisForCaseload(eq(MDI.prisonId)))
      .thenThrow(RuntimeException("Error"))
      .thenReturn(MDIResponse)

    batchService.enableNomis()

    verify(nomisService).allPrisons
    verify(nomisService, times(2)).enableNewNomisForCaseload(eq(MDI.prisonId))
    verify(telemetryClient, times(1)).trackEvent(
      eq("ApiUsersEnabled"),
      any(
        Map::class.java
      ) as MutableMap<String, String>?,
      isNull()
    )
  }

  companion object {
    private val MDI = Prison.builder().prisonId("MDI").build()
    private val LEI = Prison.builder().prisonId("LEI").build()
    private val LPI = Prison.builder().prisonId("LPI").build()
  }
}
