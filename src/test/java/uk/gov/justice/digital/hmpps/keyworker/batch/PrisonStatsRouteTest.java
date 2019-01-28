package uk.gov.justice.digital.hmpps.keyworker.batch;

import groovy.util.logging.Slf4j;
import org.apache.camel.Exchange;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.justice.digital.hmpps.keyworker.dto.Prison;
import uk.gov.justice.digital.hmpps.keyworker.model.PrisonKeyWorkerStatistic;
import uk.gov.justice.digital.hmpps.keyworker.services.KeyworkerStatsService;
import uk.gov.justice.digital.hmpps.keyworker.services.PrisonSupportedService;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.*;


@Slf4j
@RunWith(MockitoJUnitRunner.class)
public class PrisonStatsRouteTest extends CamelTestSupport {

    private final static String MOCK_PRISONS_ENDPOINT ="mock:prisons";
    private final static String MOCK_GENSTATS_ENDPOINT = "mock:gen-stats";
    private final static String MOCK_DLQ_ENDPOINT = "mock:dlq";

    private static final Prison MDI = Prison.builder().prisonId("MDI").migrated(true).build();
    private static final Prison LEI = Prison.builder().prisonId("LEI").migrated(true).build();
    private static final Prison LPI = Prison.builder().prisonId("LPI").migrated(true).build();

    @Mock
    private PrisonSupportedService prisonSupportedService;

    @Mock
    private KeyworkerStatsService keyworkerStatsService;

    @Override
    public RouteBuilder[] createRouteBuilders() throws Exception {
        MockitoAnnotations.initMocks(this);
        final PrisonStatsRoute route = new PrisonStatsRoute(keyworkerStatsService, prisonSupportedService);

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
                weaveAddLast().to(MOCK_GENSTATS_ENDPOINT);
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

        final List<Prison> prisons = List.of(
                MDI,
                LEI,
                LPI
        );

        when(prisonSupportedService.getMigratedPrisons()).thenReturn(prisons);

        LocalDate now = LocalDate.now();
        PrisonKeyWorkerStatistic mdiStats = PrisonKeyWorkerStatistic.builder().prisonId(MDI.getPrisonId()).snapshotDate(now).build();
        when(keyworkerStatsService.generatePrisonStats(MDI.getPrisonId())).thenReturn(mdiStats);
        PrisonKeyWorkerStatistic leiStats = PrisonKeyWorkerStatistic.builder().prisonId(LEI.getPrisonId()).snapshotDate(now).build();
        when(keyworkerStatsService.generatePrisonStats(LEI.getPrisonId())).thenReturn(leiStats);
        PrisonKeyWorkerStatistic lpiStats = PrisonKeyWorkerStatistic.builder().prisonId(LPI.getPrisonId()).snapshotDate(now).build();
        when(keyworkerStatsService.generatePrisonStats(LPI.getPrisonId())).thenReturn(lpiStats);

        template.send(PrisonStatsRoute.DIRECT_PRISON_STATS, exchange -> {
        });

        assertMockEndpointsSatisfied();
        final MockEndpoint mockEndpoint = getMockEndpoint(MOCK_PRISONS_ENDPOINT);
        mockEndpoint.assertIsSatisfied();

        final List<Exchange> receivedExchanges = mockEndpoint.getReceivedExchanges();
        assertEquals(1, receivedExchanges.size());
        List<Prison> exchangeData = receivedExchanges.get(0).getIn().getBody(List.class);
        assertEquals(prisons, exchangeData);

        final MockEndpoint mockEndpoint2 = getMockEndpoint(MOCK_GENSTATS_ENDPOINT);
        mockEndpoint2.assertIsSatisfied();

        final List<Exchange> receivedExchanges2 = mockEndpoint2.getReceivedExchanges();
        assertEquals(3, receivedExchanges2.size());
        assertEquals(mdiStats, receivedExchanges2.get(0).getIn().getBody(PrisonKeyWorkerStatistic.class));
        assertEquals(leiStats, receivedExchanges2.get(1).getIn().getBody(PrisonKeyWorkerStatistic.class));
        assertEquals(lpiStats, receivedExchanges2.get(2).getIn().getBody(PrisonKeyWorkerStatistic.class));

        verify(prisonSupportedService).getMigratedPrisons();
        verify(keyworkerStatsService, times(3)).generatePrisonStats(isA(String.class));
    }

    @Test
    public void testGenerateStatsCallError() throws Exception {

        final List<Prison> prisons = List.of(
                MDI,
                LEI,
                LPI
        );

        when(prisonSupportedService.getMigratedPrisons()).thenReturn(prisons);

        when(keyworkerStatsService.generatePrisonStats(MDI.getPrisonId())).thenThrow(NullPointerException.class);
        when(keyworkerStatsService.generatePrisonStats(LEI.getPrisonId())).thenReturn(PrisonKeyWorkerStatistic.builder().prisonId(LEI.getPrisonId()).build());
        when(keyworkerStatsService.generatePrisonStats(LPI.getPrisonId())).thenReturn(PrisonKeyWorkerStatistic.builder().prisonId(LPI.getPrisonId()).build());

        template.send(PrisonStatsRoute.DIRECT_PRISON_STATS, exchange -> {
        });

        assertMockEndpointsSatisfied();

        final MockEndpoint statsEndpoint = getMockEndpoint(MOCK_GENSTATS_ENDPOINT);
        statsEndpoint.assertIsSatisfied();

        final List<Exchange> receivedExchanges = statsEndpoint.getReceivedExchanges();
        assertEquals(2, receivedExchanges.size());

        final MockEndpoint dlqEndpoint = getMockEndpoint(MOCK_DLQ_ENDPOINT);
        dlqEndpoint.assertIsSatisfied();

        final List<Exchange> dlqExchanges = dlqEndpoint.getReceivedExchanges();
        assertEquals(1, dlqExchanges.size());

        verify(prisonSupportedService).getMigratedPrisons();
        verify(keyworkerStatsService, times(5)).generatePrisonStats(isA(String.class));
        verify(keyworkerStatsService).raiseStatsProcessingError(eq(MDI.getPrisonId()), isA(Exchange.class));
    }



}
