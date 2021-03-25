package uk.gov.justice.digital.hmpps.keyworker.batch;

import groovy.util.logging.Slf4j;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.justice.digital.hmpps.keyworker.services.KeyworkerBatchService;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


@Slf4j
@RunWith(MockitoJUnitRunner.class)
public class UpdateStatusRouteTest extends CamelTestSupport {

    private static final String APP_DATA = "[8, 9]";
    private final static String SUBMIT_ENDPOINT = "mock:result";

    @Mock
    private KeyworkerBatchService service;

    @Override
    public RouteBuilder[] createRouteBuilders() throws Exception {
        MockitoAnnotations.initMocks(this);
        final var updateStatusRoute = new UpdateStatusRoute(service);

        return new RouteBuilder[]{updateStatusRoute};
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
    public void testUpdateStatus() throws Exception {

        when(service.executeUpdateStatus()).thenReturn(List.of(8L,9L));

        template.send(UpdateStatusRoute.DIRECT_UPDATE_STATUS, exchange -> {
        });

        assertMockEndpointsSatisfied();
        final var mockEndpoint = getMockEndpoint(SUBMIT_ENDPOINT);
        mockEndpoint.assertIsSatisfied();

        final var receivedExchanges = mockEndpoint.getReceivedExchanges();
        assertEquals(1, receivedExchanges.size());
        final var appData = receivedExchanges.get(0).getIn().getBody(String.class);
        assertEquals(APP_DATA, appData);

        verify(service).executeUpdateStatus();
    }


}
