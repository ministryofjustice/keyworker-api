package uk.gov.justice.digital.hmpps.keyworker.services;

import com.microsoft.applicationinsights.TelemetryClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import uk.gov.justice.digital.hmpps.keyworker.config.RetryConfiguration;
import uk.gov.justice.digital.hmpps.keyworker.dto.CaseloadUpdate;
import uk.gov.justice.digital.hmpps.keyworker.dto.Prison;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {NomisBatchService.class, RetryConfiguration.class})
public class NomisBatchServiceTest {

    private static final Prison MDI = Prison.builder().prisonId("MDI").build();
    private static final Prison LEI = Prison.builder().prisonId("LEI").build();
    private static final Prison LPI = Prison.builder().prisonId("LPI").build();

    @Autowired
    private NomisBatchService batchService;

    @MockBean
    private NomisService nomisService;

    @MockBean
    private TelemetryClient telemetryClient;

    @Test
    public void enableNomis_makesPrisonApiCalls() {

        final var prisons = List.of(MDI, LEI, LPI);

        when(nomisService.getAllPrisons()).thenReturn(prisons);
        final var MDIResponse = CaseloadUpdate.builder().caseload(MDI.getPrisonId()).numUsersEnabled(2).build();
        when(nomisService.enableNewNomisForCaseload(eq(MDI.getPrisonId()))).thenReturn(MDIResponse);
        final var LEIResponse = CaseloadUpdate.builder().caseload(LEI.getPrisonId()).numUsersEnabled(0).build();
        when(nomisService.enableNewNomisForCaseload(eq(LEI.getPrisonId()))).thenReturn(LEIResponse);
        final var LPIResponse = CaseloadUpdate.builder().caseload(LPI.getPrisonId()).numUsersEnabled(14).build();
        when(nomisService.enableNewNomisForCaseload(eq(LPI.getPrisonId()))).thenReturn(LPIResponse);

        batchService.enableNomis();

        verify(nomisService).getAllPrisons();
        verify(nomisService).enableNewNomisForCaseload(eq(MDI.getPrisonId()));
        verify(nomisService).enableNewNomisForCaseload(eq(LEI.getPrisonId()));
        verify(nomisService).enableNewNomisForCaseload(eq(LPI.getPrisonId()));
        verify(telemetryClient, times(3)).trackEvent(eq("ApiUsersEnabled"), isA(Map.class), isNull());
    }

    @Test
    public void testEnabledNewNomisCamelRoute_NoOpOnGetAllPrisonsError() {

        when(nomisService.getAllPrisons()).thenThrow(new RuntimeException("Error"));

        batchService.enableNomis();

        verify(nomisService).getAllPrisons();
        verify(nomisService, times(0)).enableNewNomisForCaseload(anyString());
        verify(telemetryClient, times(0)).trackEvent(anyString(), any(Map.class), isNull());
    }

    @Test
    public void testEnabledNewNomisCamelRoute_RetriesOnEnablePrisonsError() {

        final var prisons = List.of(MDI);

        when(nomisService.getAllPrisons()).thenReturn(prisons);
        final var MDIResponse = CaseloadUpdate.builder().caseload(MDI.getPrisonId()).numUsersEnabled(2).build();
        when(nomisService.enableNewNomisForCaseload(eq(MDI.getPrisonId())))
            .thenThrow(new RuntimeException("Error"))
            .thenReturn(MDIResponse);

        batchService.enableNomis();

        verify(nomisService).getAllPrisons();
        verify(nomisService, times(2)).enableNewNomisForCaseload(eq(MDI.getPrisonId()));
        verify(telemetryClient, times(1)).trackEvent(eq("ApiUsersEnabled"), isA(Map.class), isNull());
    }
}
