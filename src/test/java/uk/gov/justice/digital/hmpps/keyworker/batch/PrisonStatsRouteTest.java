package uk.gov.justice.digital.hmpps.keyworker.batch;

import groovy.util.logging.Slf4j;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.justice.digital.hmpps.keyworker.services.KeyworkerStatsBatchService;

import static org.mockito.Mockito.verify;


@Slf4j
@RunWith(MockitoJUnitRunner.class)
public class PrisonStatsRouteTest extends CamelTestSupport {

    private final static String MOCK_GENSTATS_ENDPOINT = "mock:gen-stats";

    @Mock
    private KeyworkerStatsBatchService keyworkerStatsBatchService;

    @Override
    public RouteBuilder[] createRouteBuilders() {
        MockitoAnnotations.initMocks(this);
        final var route = new PrisonStatsRoute(keyworkerStatsBatchService);

        return new RouteBuilder[]{route};
    }

    @Before
    public void mockEndpoints() throws Exception {
        AdviceWith.adviceWith(context, null, a -> {
            a.weaveAddLast().to(MOCK_GENSTATS_ENDPOINT);
        });
    }

    @Test
    public void testGenerateStatsCall() throws Exception {

        template.send(PrisonStatsRoute.DIRECT_PRISON_STATS, exchange -> {
        });

        assertMockEndpointsSatisfied();

        final var statsEndpoint = getMockEndpoint(MOCK_GENSTATS_ENDPOINT);
        statsEndpoint.assertIsSatisfied();

        final var receivedExchanges = statsEndpoint.getReceivedExchanges();
        assertEquals(1, receivedExchanges.size());

        verify(keyworkerStatsBatchService).generatePrisonStats();
    }

}
