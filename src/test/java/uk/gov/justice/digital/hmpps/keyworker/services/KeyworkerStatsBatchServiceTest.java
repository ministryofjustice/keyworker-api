package uk.gov.justice.digital.hmpps.keyworker.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import uk.gov.justice.digital.hmpps.keyworker.config.RetryConfiguration;
import uk.gov.justice.digital.hmpps.keyworker.dto.Prison;
import uk.gov.justice.digital.hmpps.keyworker.model.PrisonKeyWorkerStatistic;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {KeyworkerStatsBatchService.class, RetryConfiguration.class})
public class KeyworkerStatsBatchServiceTest {

    private static final Prison MDI = Prison.builder().prisonId("MDI").build();
    private static final Prison LEI = Prison.builder().prisonId("LEI").build();
    private static final Prison LPI = Prison.builder().prisonId("LPI").build();

    @Autowired
    private KeyworkerStatsBatchService batchService;

    @MockBean
    private KeyworkerStatsService keyworkerStatsService;

    @MockBean
    private PrisonSupportedService prisonSupportedService;

    @Test
    public void testGenerateStatsCall_callsServices() {

        final var prisons = List.of(
            MDI,
            LEI,
            LPI
        );

        when(prisonSupportedService.getMigratedPrisons()).thenReturn(prisons);

        final var now = LocalDate.now();
        final var mdiStats = PrisonKeyWorkerStatistic.builder().prisonId(MDI.getPrisonId()).snapshotDate(now).build();
        when(keyworkerStatsService.generatePrisonStats(MDI.getPrisonId())).thenReturn(mdiStats);
        final var leiStats = PrisonKeyWorkerStatistic.builder().prisonId(LEI.getPrisonId()).snapshotDate(now).build();
        when(keyworkerStatsService.generatePrisonStats(LEI.getPrisonId())).thenReturn(leiStats);
        final var lpiStats = PrisonKeyWorkerStatistic.builder().prisonId(LPI.getPrisonId()).snapshotDate(now).build();
        when(keyworkerStatsService.generatePrisonStats(LPI.getPrisonId())).thenReturn(lpiStats);

        batchService.generatePrisonStats();

        verify(prisonSupportedService).getMigratedPrisons();
        verify(keyworkerStatsService, times(3)).generatePrisonStats(isA(String.class));
        verify(keyworkerStatsService, never()).raiseStatsProcessingError(anyString(), any());
    }

    @Test
    public void testGenerateStatsCall_noOpOnGetMigratedPrisonsError() {

        when(prisonSupportedService.getMigratedPrisons()).thenThrow(new RuntimeException("Error"));

        batchService.generatePrisonStats();

        verify(prisonSupportedService).getMigratedPrisons();
        verify(keyworkerStatsService, never()).generatePrisonStats(anyString());
        verify(keyworkerStatsService, never()).raiseStatsProcessingError(anyString(), any());
    }

    @Test
    public void testGenerateStatsCall_retriesOnGenerateStatsError() {

        final var prisons = List.of(
            MDI
        );

        when(prisonSupportedService.getMigratedPrisons()).thenReturn(prisons);

        when(keyworkerStatsService.generatePrisonStats(MDI.getPrisonId()))
            .thenThrow(NullPointerException.class)
            .thenReturn(PrisonKeyWorkerStatistic.builder().prisonId(MDI.getPrisonId()).build());

        batchService.generatePrisonStats();

        verify(prisonSupportedService).getMigratedPrisons();
        verify(keyworkerStatsService, times(2)).generatePrisonStats(isA(String.class));
        verify(keyworkerStatsService, never()).raiseStatsProcessingError(anyString(), any());
    }

    @Test
    public void testGenerateStatsCall_raisesProcessingErrorOnGenerateStatsError() {

        final var prisons = List.of(
            MDI,
            LEI,
            LPI
        );

        when(prisonSupportedService.getMigratedPrisons()).thenReturn(prisons);

        when(keyworkerStatsService.generatePrisonStats(MDI.getPrisonId())).thenThrow(NullPointerException.class);
        when(keyworkerStatsService.generatePrisonStats(LEI.getPrisonId())).thenReturn(PrisonKeyWorkerStatistic.builder().prisonId(LEI.getPrisonId()).build());
        when(keyworkerStatsService.generatePrisonStats(LPI.getPrisonId())).thenReturn(PrisonKeyWorkerStatistic.builder().prisonId(LPI.getPrisonId()).build());

        batchService.generatePrisonStats();

        verify(prisonSupportedService).getMigratedPrisons();
        verify(keyworkerStatsService, times(5)).generatePrisonStats(isA(String.class));
        verify(keyworkerStatsService).raiseStatsProcessingError(eq(MDI.getPrisonId()), isA(Exception.class));
    }
}
