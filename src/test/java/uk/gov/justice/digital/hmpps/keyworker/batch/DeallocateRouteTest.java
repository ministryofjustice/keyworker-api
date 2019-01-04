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
import uk.gov.justice.digital.hmpps.keyworker.services.KeyworkerBatchService;

import java.util.List;

import static org.mockito.Mockito.verify;


@Slf4j
@RunWith(MockitoJUnitRunner.class)
public class DeallocateRouteTest extends CamelTestSupport {

    private final static String SUBMIT_ENDPOINT = "mock:result";

    @Mock
    private KeyworkerBatchService service;

    @Override
    public RouteBuilder[] createRouteBuilders() throws Exception {
        MockitoAnnotations.initMocks(this);
        final DeallocationRoute deallocationRoute = new DeallocationRoute(service);

        return new RouteBuilder[]{deallocationRoute};
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

    @Override
    public boolean isUseAdviceWith() {
        return true;
    }


    @Test
    public void testRoute() throws Exception {

        context.start();

        template.send(DeallocationRoute.DIRECT_DEALLOCATION, exchange -> {
        });

        assertMockEndpointsSatisfied();
        final MockEndpoint mockEndpoint = getMockEndpoint(SUBMIT_ENDPOINT);
        mockEndpoint.assertIsSatisfied();

        final List<Exchange> receivedExchanges = mockEndpoint.getReceivedExchanges();
        assertEquals(1, receivedExchanges.size());

        verify(service).executeDeallocation();

        context.stop();
    }


}
