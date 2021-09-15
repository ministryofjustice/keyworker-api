package uk.gov.justice.digital.hmpps.keyworker.services

import com.nhaarman.mockitokotlin2.whenever
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.ArgumentMatchers.isA
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit.jupiter.SpringExtension
import uk.gov.justice.digital.hmpps.keyworker.config.RetryConfiguration
import uk.gov.justice.digital.hmpps.keyworker.dto.Prison
import uk.gov.justice.digital.hmpps.keyworker.model.PrisonKeyWorkerStatistic
import java.time.LocalDate

@ExtendWith(SpringExtension::class)
@ContextConfiguration(classes = [KeyworkerStatsBatchService::class, RetryConfiguration::class])
class KeyworkerStatsBatchServiceTest {
  @Autowired
  private lateinit var batchService: KeyworkerStatsBatchService

  @MockBean
  private lateinit var keyworkerStatsService: KeyworkerStatsService

  @MockBean
  private lateinit var prisonSupportedService: PrisonSupportedService

  @Test
  fun testGenerateStatsCall_callsServices() {
    val prisons = listOf(
      MDI,
      LEI,
      LPI
    )
    whenever(prisonSupportedService.migratedPrisons).thenReturn(prisons)
    val now = LocalDate.now()
    val mdiStats = PrisonKeyWorkerStatistic.builder().prisonId(MDI.prisonId).snapshotDate(now).build()
    whenever(keyworkerStatsService.generatePrisonStats(MDI.prisonId)).thenReturn(mdiStats)
    val leiStats = PrisonKeyWorkerStatistic.builder().prisonId(LEI.prisonId).snapshotDate(now).build()
    whenever(keyworkerStatsService.generatePrisonStats(LEI.prisonId)).thenReturn(leiStats)
    val lpiStats = PrisonKeyWorkerStatistic.builder().prisonId(LPI.prisonId).snapshotDate(now).build()
    whenever(keyworkerStatsService.generatePrisonStats(LPI.prisonId)).thenReturn(lpiStats)

    batchService.generatePrisonStats()

    verify(prisonSupportedService).migratedPrisons
    verify(keyworkerStatsService, times(3)).generatePrisonStats(
      isA(
        String::class.java
      )
    )
    verify(keyworkerStatsService, never())
      .raiseStatsProcessingError(anyString(), any())
  }

  @Test
  fun testGenerateStatsCall_noOpOnGetMigratedPrisonsError() {
    whenever(prisonSupportedService.migratedPrisons).thenThrow(RuntimeException("Error"))

    batchService.generatePrisonStats()

    verify(prisonSupportedService).migratedPrisons
    verify(keyworkerStatsService, never()).generatePrisonStats(anyString())
    verify(keyworkerStatsService, never())
      .raiseStatsProcessingError(anyString(), any())
  }

  @Test
  fun testGenerateStatsCall_retriesOnGenerateStatsError() {
    val prisons = listOf(
      MDI
    )
    whenever(prisonSupportedService.migratedPrisons).thenReturn(prisons)
    whenever(keyworkerStatsService.generatePrisonStats(MDI.prisonId))
      .thenThrow(NullPointerException::class.java)
      .thenReturn(PrisonKeyWorkerStatistic.builder().prisonId(MDI.prisonId).build())

    batchService.generatePrisonStats()

    verify(prisonSupportedService).migratedPrisons
    verify(keyworkerStatsService, times(2)).generatePrisonStats(
      isA(
        String::class.java
      )
    )
    verify(keyworkerStatsService, never())
      .raiseStatsProcessingError(anyString(), any())
  }

  @Test
  fun testGenerateStatsCall_raisesProcessingErrorOnGenerateStatsError() {
    val prisons = listOf(
      MDI,
      LEI,
      LPI
    )
    whenever(prisonSupportedService.migratedPrisons).thenReturn(prisons)
    whenever(keyworkerStatsService.generatePrisonStats(MDI.prisonId)).thenThrow(
      NullPointerException::class.java
    )
    whenever(keyworkerStatsService.generatePrisonStats(LEI.prisonId)).thenReturn(
      PrisonKeyWorkerStatistic.builder().prisonId(
        LEI.prisonId
      ).build()
    )
    whenever(keyworkerStatsService.generatePrisonStats(LPI.prisonId)).thenReturn(
      PrisonKeyWorkerStatistic.builder().prisonId(
        LPI.prisonId
      ).build()
    )

    batchService.generatePrisonStats()

    verify(prisonSupportedService).migratedPrisons
    verify(keyworkerStatsService, times(5)).generatePrisonStats(
      isA(
        String::class.java
      )
    )
    verify(keyworkerStatsService).raiseStatsProcessingError(
      eq(MDI.prisonId),
      isA(
        Exception::class.java
      )
    )
  }

  companion object {
    private val MDI = Prison.builder().prisonId("MDI").build()
    private val LEI = Prison.builder().prisonId("LEI").build()
    private val LPI = Prison.builder().prisonId("LPI").build()
  }
}
