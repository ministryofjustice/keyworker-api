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
import org.mockito.runners.MockitoJUnitRunner;
import uk.gov.justice.digital.hmpps.keyworker.dto.Prison;
import uk.gov.justice.digital.hmpps.keyworker.model.PrisonKeyWorkerStatistic;
import uk.gov.justice.digital.hmpps.keyworker.services.KeyworkerStatsService;
import uk.gov.justice.digital.hmpps.keyworker.services.PrisonSupportedService;

import java.util.Arrays;
import java.util.List;

import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.*;


@Slf4j
@RunWith(MockitoJUnitRunner.class)
public class PrisonStatsRouteTest extends CamelTestSupport {

    private static final String APP_DATA = "[8, 9]";
    private final static String SUBMIT_ENDPOINT = "mock:result";

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
                weaveAddLast().to("mock:result");
            }
        });
    }


    @Test
    public void testGenerateStatsCall() throws Exception {

        final List<Prison> prisons = Arrays.asList(
                MDI,
                LEI,
                LPI
        );

        when(prisonSupportedService.getMigratedPrisons()).thenReturn(prisons);

        when(keyworkerStatsService.generatePrisonStats(MDI.getPrisonId())).thenReturn(PrisonKeyWorkerStatistic.builder().prisonId(MDI.getPrisonId()).build());
        when(keyworkerStatsService.generatePrisonStats(LEI.getPrisonId())).thenReturn(PrisonKeyWorkerStatistic.builder().prisonId(LEI.getPrisonId()).build());
        when(keyworkerStatsService.generatePrisonStats(LPI.getPrisonId())).thenReturn(PrisonKeyWorkerStatistic.builder().prisonId(LPI.getPrisonId()).build());

        template.send(PrisonStatsRoute.DIRECT_PRISON_STATS, exchange -> {
        });

        assertMockEndpointsSatisfied();
        final MockEndpoint mockEndpoint = getMockEndpoint(SUBMIT_ENDPOINT);
        mockEndpoint.assertIsSatisfied();

        final List<Exchange> receivedExchanges = mockEndpoint.getReceivedExchanges();
        assertEquals(1, receivedExchanges.size());
        List<Prison> exchangeData = receivedExchanges.get(0).getIn().getBody(List.class);
        assertEquals(prisons, exchangeData);

        verify(prisonSupportedService).getMigratedPrisons();
        verify(keyworkerStatsService, times(3)).generatePrisonStats(isA(String.class));
    }


}
