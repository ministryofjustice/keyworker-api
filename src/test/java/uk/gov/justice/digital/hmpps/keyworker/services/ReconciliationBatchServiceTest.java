package uk.gov.justice.digital.hmpps.keyworker.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import uk.gov.justice.digital.hmpps.keyworker.config.RetryConfiguration;
import uk.gov.justice.digital.hmpps.keyworker.dto.Prison;
import uk.gov.justice.digital.hmpps.keyworker.services.ReconciliationService.ReconMetrics;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {ReconciliationBatchService.class, RetryConfiguration.class})
public class ReconciliationBatchServiceTest {

    private static final Prison MDI = Prison.builder().prisonId("MDI").build();
    private static final Prison LEI = Prison.builder().prisonId("LEI").build();
    private static final Prison LPI = Prison.builder().prisonId("LPI").build();

    @Autowired
    private ReconciliationBatchService batchService;

    @MockBean
    private ReconciliationService reconciliationService;

    @MockBean
    private PrisonSupportedService prisonSupportedService;

    @Test
    public void testReconcileKeyWorkerAllocations_callsServices() {

        final var prisons = List.of(
            MDI,
            LEI,
            LPI
        );

        when(prisonSupportedService.getMigratedPrisons()).thenReturn(prisons);

        when(reconciliationService.reconcileKeyWorkerAllocations(MDI.getPrisonId())).thenReturn(new ReconMetrics(MDI.getPrisonId(), 10, 0));
        when(reconciliationService.reconcileKeyWorkerAllocations(LEI.getPrisonId())).thenReturn(new ReconMetrics(LEI.getPrisonId(), 5, 1));
        when(reconciliationService.reconcileKeyWorkerAllocations(LPI.getPrisonId())).thenReturn(new ReconMetrics(LPI.getPrisonId(), 3, 2));

        batchService.reconcileKeyWorkerAllocations();

        verify(prisonSupportedService).getMigratedPrisons();
        verify(reconciliationService).reconcileKeyWorkerAllocations(eq(MDI.getPrisonId()));
        verify(reconciliationService).reconcileKeyWorkerAllocations(eq(LEI.getPrisonId()));
        verify(reconciliationService).reconcileKeyWorkerAllocations(eq(LPI.getPrisonId()));
        verify(reconciliationService, times(0)).raiseProcessingError(anyString(), any());
    }

    @Test
    public void testReconcileKeyWorkerAllocations_npOpOnMigratedPrisonsError() {

        when(prisonSupportedService.getMigratedPrisons()).thenThrow(new RuntimeException("Error"));

        batchService.reconcileKeyWorkerAllocations();

        verify(prisonSupportedService).getMigratedPrisons();
        verify(reconciliationService, times(0)).reconcileKeyWorkerAllocations(anyString());
        verify(reconciliationService, times(0)).raiseProcessingError(anyString(), any());
    }

    @Test
    public void testReconcileKeyWorkerAllocations_retriesOnReconcileAllocationsError() {

        final var prisons = List.of(
            MDI
        );

        when(prisonSupportedService.getMigratedPrisons()).thenReturn(prisons);

        when(reconciliationService.reconcileKeyWorkerAllocations(MDI.getPrisonId()))
            .thenThrow(new RuntimeException("Error"))
            .thenReturn(new ReconMetrics(MDI.getPrisonId(), 10, 0));

        batchService.reconcileKeyWorkerAllocations();

        verify(prisonSupportedService).getMigratedPrisons();
        verify(reconciliationService, times(2)).reconcileKeyWorkerAllocations(eq(MDI.getPrisonId()));
        verify(reconciliationService, times(0)).raiseProcessingError(anyString(), any());
    }

    @Test
    public void testReconcileKeyWorkerAllocations_raisesProcessingErrorOnReconcileAllocationsError() {

        final var prisons = List.of(
            MDI
        );
        final var testException = new RuntimeException("Error");

        when(prisonSupportedService.getMigratedPrisons()).thenReturn(prisons);

        when(reconciliationService.reconcileKeyWorkerAllocations(MDI.getPrisonId()))
            .thenThrow(testException);

        batchService.reconcileKeyWorkerAllocations();

        verify(prisonSupportedService).getMigratedPrisons();
        verify(reconciliationService, times(3)).reconcileKeyWorkerAllocations(eq(MDI.getPrisonId()));

        verify(reconciliationService).raiseProcessingError(eq(MDI.getPrisonId()), eq(testException));
    }

}
