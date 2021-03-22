package uk.gov.justice.digital.hmpps.keyworker.batch;

import groovy.util.logging.Slf4j;
import org.apache.camel.Exchange;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.justice.digital.hmpps.keyworker.dto.Prison;
import uk.gov.justice.digital.hmpps.keyworker.services.PrisonSupportedService;
import uk.gov.justice.digital.hmpps.keyworker.services.ReconciliationService;
import uk.gov.justice.digital.hmpps.keyworker.services.ReconciliationService.ReconMetrics;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.times;


@Slf4j
@RunWith(MockitoJUnitRunner.class)
public class KeyworkerReconRouteTest extends CamelTestSupport {

    private final static String MOCK_PRISONS_ENDPOINT ="mock:prisons";
    private final static String MOCK_RECON_ENDPOINT = "mock:recon";
    private final static String MOCK_DLQ_ENDPOINT = "mock:dlq";

    private static final Prison MDI = Prison.builder().prisonId("MDI").migrated(true).build();
    private static final Prison LEI = Prison.builder().prisonId("LEI").migrated(true).build();
    private static final Prison LPI = Prison.builder().prisonId("LPI").migrated(true).build();

    @Mock
    private PrisonSupportedService prisonSupportedService;

    @Mock
    private ReconciliationService reconciliationService;

    @Override
    public RouteBuilder[] createRouteBuilders() throws Exception {
        MockitoAnnotations.initMocks(this);
        final var route = new KeyworkerReconRoute(reconciliationService, prisonSupportedService);

        return new RouteBuilder[]{route};
    }

    @Before
    public void mockEndpoints() throws Exception {
        context.getRouteDefinitions().get(0).adviceWith(context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                weaveAddLast().to(MOCK_PRISONS_ENDPOINT);
            }
        });

        context.getRouteDefinitions().get(1).adviceWith(context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                weaveAddLast().to(MOCK_RECON_ENDPOINT);
            }
        });

        context.getRouteDefinitions().get(2).adviceWith(context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                weaveAddLast().to(MOCK_DLQ_ENDPOINT);
            }
        });
    }


    @Test
    public void testGenerateStatsCall() throws Exception {

        final var prisons = List.of(
                MDI,
                LEI,
                LPI
        );

        when(prisonSupportedService.getMigratedPrisons()).thenReturn(prisons);

        when(reconciliationService.reconcileKeyWorkerAllocations(MDI.getPrisonId())).thenReturn(new ReconMetrics(MDI.getPrisonId(), 10, 0));
        when(reconciliationService.reconcileKeyWorkerAllocations(LEI.getPrisonId())).thenReturn(new ReconMetrics(LEI.getPrisonId(), 5, 1));
        when(reconciliationService.reconcileKeyWorkerAllocations(LPI.getPrisonId())).thenReturn(new ReconMetrics(LPI.getPrisonId(), 3, 2));

        template.send(KeyworkerReconRoute.DIRECT_KEY_WORKER_RECON, exchange -> {
        });

        assertMockEndpointsSatisfied();
        final var mockEndpoint = getMockEndpoint(MOCK_PRISONS_ENDPOINT);
        mockEndpoint.assertIsSatisfied();

        final var receivedExchanges = mockEndpoint.getReceivedExchanges();
        assertEquals(1, receivedExchanges.size());
        final List<Prison> exchangeData = receivedExchanges.get(0).getIn().getBody(List.class);
        assertEquals(prisons, exchangeData);

        final var mockEndpoint2 = getMockEndpoint(MOCK_RECON_ENDPOINT);
        mockEndpoint2.assertIsSatisfied();

        final var receivedExchanges2 = mockEndpoint2.getReceivedExchanges();
        assertEquals(3, receivedExchanges2.size());

        verify(prisonSupportedService).getMigratedPrisons();
        verify(reconciliationService).reconcileKeyWorkerAllocations(eq(MDI.getPrisonId()));
        verify(reconciliationService).reconcileKeyWorkerAllocations(eq(LEI.getPrisonId()));
        verify(reconciliationService).reconcileKeyWorkerAllocations(eq(LPI.getPrisonId()));
        verify(reconciliationService, times(0)).raiseProcessingError(anyString(), any());
    }

    @Test
    public void testGenerateStatsCall_NoOpOnGetMigratedPrisonsError() throws Exception {

        when(prisonSupportedService.getMigratedPrisons()).thenThrow(new RuntimeException("Error"));

        template.send(KeyworkerReconRoute.DIRECT_KEY_WORKER_RECON, exchange -> {
        });

        assertMockEndpointsSatisfied();
        final var mockEndpoint = getMockEndpoint(MOCK_PRISONS_ENDPOINT);
        mockEndpoint.assertIsSatisfied();

        verify(prisonSupportedService).getMigratedPrisons();
    }

    @Test
    public void testGenerateStatsCall_RetriesOnReconcileAllocationsError() throws Exception {

        final var prisons = List.of(
            MDI
        );

        when(prisonSupportedService.getMigratedPrisons()).thenReturn(prisons);

        when(reconciliationService.reconcileKeyWorkerAllocations(MDI.getPrisonId()))
            .thenThrow(new RuntimeException("Error"))
            .thenReturn(new ReconMetrics(MDI.getPrisonId(), 10, 0));

        template.send(KeyworkerReconRoute.DIRECT_KEY_WORKER_RECON, exchange -> {
        });

        assertMockEndpointsSatisfied();
        final var mockEndpoint = getMockEndpoint(MOCK_PRISONS_ENDPOINT);
        mockEndpoint.assertIsSatisfied();

        final var receivedExchanges = mockEndpoint.getReceivedExchanges();
        assertEquals(1, receivedExchanges.size());
        final List<Prison> exchangeData = receivedExchanges.get(0).getIn().getBody(List.class);
        assertEquals(prisons, exchangeData);

        final var mockEndpoint2 = getMockEndpoint(MOCK_RECON_ENDPOINT);
        mockEndpoint2.assertIsSatisfied();

        final var receivedExchanges2 = mockEndpoint2.getReceivedExchanges();
        assertEquals(1, receivedExchanges2.size());

        verify(prisonSupportedService).getMigratedPrisons();
        verify(reconciliationService, times(2)).reconcileKeyWorkerAllocations(eq(MDI.getPrisonId()));
        verify(reconciliationService, times(0)).raiseProcessingError(anyString(), any());
    }

    @Test
    public void testGenerateStatsCall_RaisesProcessingErrorOnReconcileAllocationsError() throws Exception {

        final var prisons = List.of(
            MDI
        );

        when(prisonSupportedService.getMigratedPrisons()).thenReturn(prisons);

        when(reconciliationService.reconcileKeyWorkerAllocations(MDI.getPrisonId()))
            .thenThrow(new RuntimeException("Error"));

        template.send(KeyworkerReconRoute.DIRECT_KEY_WORKER_RECON, exchange -> {
        });

        assertMockEndpointsSatisfied();
        final var mockEndpoint = getMockEndpoint(MOCK_PRISONS_ENDPOINT);
        mockEndpoint.assertIsSatisfied();

        final var receivedExchanges = mockEndpoint.getReceivedExchanges();
        assertEquals(1, receivedExchanges.size());
        final List<Prison> exchangeData = receivedExchanges.get(0).getIn().getBody(List.class);
        assertEquals(prisons, exchangeData);

        final var mockEndpoint2 = getMockEndpoint(MOCK_RECON_ENDPOINT);
        mockEndpoint2.assertIsSatisfied();

        final var receivedExchanges2 = mockEndpoint2.getReceivedExchanges();
        assertEquals(0, receivedExchanges2.size());

        verify(prisonSupportedService).getMigratedPrisons();
        verify(reconciliationService, times(3)).reconcileKeyWorkerAllocations(eq(MDI.getPrisonId()));

        ArgumentCaptor<String> prisonArgument = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Exchange> exchangeArgument = ArgumentCaptor.forClass(Exchange.class);
        verify(reconciliationService).raiseProcessingError(eq(MDI.getPrisonId()), isA(Exchange.class));
    }

}
