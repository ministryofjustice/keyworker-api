package uk.gov.justice.digital.hmpps.keyworker.services

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.ArgumentMatchers.isA
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.kotlin.whenever
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
    val prisons =
      listOf(
        MDI,
        LEI,
        LPI,
      )
    whenever(prisonSupportedService.migratedPrisons).thenReturn(prisons)
    val now = LocalDate.now()
    val mdiStats = PrisonKeyWorkerStatistic.builder().prisonId(MDI.prisonId).snapshotDate(now).build()
    whenever(keyworkerStatsService.generatePrisonStats(MDI.prisonId, now)).thenReturn(mdiStats)
    val leiStats = PrisonKeyWorkerStatistic.builder().prisonId(LEI.prisonId).snapshotDate(now).build()
    whenever(keyworkerStatsService.generatePrisonStats(LEI.prisonId, now)).thenReturn(leiStats)
    val lpiStats = PrisonKeyWorkerStatistic.builder().prisonId(LPI.prisonId).snapshotDate(now).build()
    whenever(keyworkerStatsService.generatePrisonStats(LPI.prisonId, now)).thenReturn(lpiStats)

    batchService.generatePrisonStats(snapshotDate = now)

    verify(prisonSupportedService).migratedPrisons
    verify(keyworkerStatsService, times(3)).generatePrisonStats(
      isA(
        String::class.java,
      ),
      eq(LocalDate.now()),
    )
    verify(keyworkerStatsService, never())
      .raiseStatsProcessingError(anyString(), any())
  }

  @Test
  fun testGenerateStatsCall_noOpOnGetMigratedPrisonsError() {
    whenever(prisonSupportedService.migratedPrisons).thenThrow(RuntimeException("Error"))
    val now = LocalDate.now()
    batchService.generatePrisonStats(now)

    verify(prisonSupportedService).migratedPrisons
    verify(keyworkerStatsService, never()).generatePrisonStats(anyString(), eq(LocalDate.now()))
    verify(keyworkerStatsService, never())
      .raiseStatsProcessingError(anyString(), any())
  }

  @Test
  fun testGenerateStatsCall_retriesOnGenerateStatsError() {
    val prisons =
      listOf(
        MDI,
      )
    val now = LocalDate.now()
    whenever(prisonSupportedService.migratedPrisons).thenReturn(prisons)
    whenever(keyworkerStatsService.generatePrisonStats(MDI.prisonId, now))
      .thenThrow(NullPointerException::class.java)
      .thenReturn(PrisonKeyWorkerStatistic.builder().prisonId(MDI.prisonId).build())

    batchService.generatePrisonStats(now)

    verify(prisonSupportedService).migratedPrisons
    verify(keyworkerStatsService, times(2)).generatePrisonStats(
      isA(
        String::class.java,
      ),
      eq(LocalDate.now()),
    )
    verify(keyworkerStatsService, never())
      .raiseStatsProcessingError(anyString(), any())
  }

  @Test
  fun testGenerateStatsCall_raisesProcessingErrorOnGenerateStatsError() {
    val prisons =
      listOf(
        MDI,
        LEI,
        LPI,
      )
    val now = LocalDate.now()
    whenever(prisonSupportedService.migratedPrisons).thenReturn(prisons)
    whenever(keyworkerStatsService.generatePrisonStats(MDI.prisonId, now)).thenThrow(
      NullPointerException::class.java,
    )
    whenever(keyworkerStatsService.generatePrisonStats(LEI.prisonId, now)).thenReturn(
      PrisonKeyWorkerStatistic.builder().prisonId(
        LEI.prisonId,
      ).build(),
    )
    whenever(keyworkerStatsService.generatePrisonStats(LPI.prisonId, now)).thenReturn(
      PrisonKeyWorkerStatistic.builder().prisonId(
        LPI.prisonId,
      ).build(),
    )

    batchService.generatePrisonStats(now)

    verify(prisonSupportedService).migratedPrisons
    verify(keyworkerStatsService, times(5)).generatePrisonStats(
      isA(
        String::class.java,
      ),
      eq(LocalDate.now()),
    )
    verify(keyworkerStatsService).raiseStatsProcessingError(
      eq(MDI.prisonId),
      isA(
        Exception::class.java,
      ),
    )
  }

  companion object {
    private val MDI = Prison.builder().prisonId("MDI").build()
    private val LEI = Prison.builder().prisonId("LEI").build()
    private val LPI = Prison.builder().prisonId("LPI").build()
  }
}
