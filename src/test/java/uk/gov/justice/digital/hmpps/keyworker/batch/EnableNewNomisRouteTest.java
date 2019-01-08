package uk.gov.justice.digital.hmpps.keyworker.batch;

import com.microsoft.applicationinsights.TelemetryClient;
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
import uk.gov.justice.digital.hmpps.keyworker.services.NomisService;

import java.util.List;

import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.*;


@Slf4j
@RunWith(MockitoJUnitRunner.class)
public class EnableNewNomisRouteTest extends CamelTestSupport {

    private final static String MOCK_PRISONS_ENDPOINT ="mock:getAllPrisons";
    private final static String MOCK_ENABLE_ENDPOINT = "mock:enableNewNomis";
    private final static String MOCK_DLQ_ENDPOINT = "mock:dlq";

    private static final Prison MDI = Prison.builder().prisonId("MDI").build();
    private static final Prison LEI = Prison.builder().prisonId("LEI").build();
    private static final Prison LPI = Prison.builder().prisonId("LPI").build();

    @Mock
    private NomisService nomisService;

    @Mock
    private TelemetryClient telemetryClient;

    @Override
    public RouteBuilder[] createRouteBuilders() {
        MockitoAnnotations.initMocks(this);
        final EnableNewNomisRoute route = new EnableNewNomisRoute(nomisService, telemetryClient);
        return new RouteBuilder[]{route};
    }

    @Before
    public void mockEndpoints() throws Exception {
        context.getRouteDefinitions().get(0).adviceWith(context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() {
                weaveAddLast().to(MOCK_PRISONS_ENDPOINT);
            }
        });

        context.getRouteDefinitions().get(1).adviceWith(context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() {
                weaveAddLast().to(MOCK_ENABLE_ENDPOINT);
            }
        });

        context.getRouteDefinitions().get(2).adviceWith(context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() {
                weaveAddLast().to(MOCK_DLQ_ENDPOINT);
            }
        });
    }


    @Test
    public void testGenerateStatsCall() throws Exception {

        var prisons = List.of( MDI, LEI, LPI );

        when(nomisService.getAllPrisons()).thenReturn(prisons);
        when(nomisService.enableNewNomisForCaseload(eq(MDI.getPrisonId()))).thenReturn(2);
        when(nomisService.enableNewNomisForCaseload(eq(LEI.getPrisonId()))).thenReturn(0);
        when(nomisService.enableNewNomisForCaseload(eq(LPI.getPrisonId()))).thenReturn(14);


        template.send(EnableNewNomisRoute.ENABLE_NEW_NOMIS, exchange -> {
        });

        assertMockEndpointsSatisfied();
        var mockEndpoint = getMockEndpoint(MOCK_PRISONS_ENDPOINT);
        mockEndpoint.assertIsSatisfied();

        var receivedExchanges = mockEndpoint.getReceivedExchanges();
        assertEquals(1, receivedExchanges.size());
        List<Prison> exchangeData = receivedExchanges.get(0).getIn().getBody(List.class);
        assertEquals(prisons, exchangeData);

        final MockEndpoint mockEndpoint2 = getMockEndpoint(MOCK_ENABLE_ENDPOINT);
        mockEndpoint2.assertIsSatisfied();

        final List<Exchange> receivedExchanges2 = mockEndpoint2.getReceivedExchanges();
        assertEquals(3, receivedExchanges2.size());

        assertEquals(receivedExchanges2.get(0).getIn().getBody(Integer.class), Integer.valueOf(2));
        assertEquals(receivedExchanges2.get(1).getIn().getBody(Integer.class), Integer.valueOf(0));
        assertEquals(receivedExchanges2.get(2).getIn().getBody(Integer.class), Integer.valueOf(14));

        verify(nomisService).getAllPrisons();
        verify(nomisService, times(3)).enableNewNomisForCaseload(isA(String.class));
    }


}
