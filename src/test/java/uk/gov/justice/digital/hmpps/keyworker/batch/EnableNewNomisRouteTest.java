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
import uk.gov.justice.digital.hmpps.keyworker.services.NomisBatchService;

import static org.mockito.Mockito.verify;


@Slf4j
@RunWith(MockitoJUnitRunner.class)
public class EnableNewNomisRouteTest extends CamelTestSupport {

    private final static String MOCK_ENABLE_ENDPOINT = "mock:enableNewNomis";

    @Mock
    private NomisBatchService nomisBatchService;

    @Override
    public RouteBuilder[] createRouteBuilders() {
        MockitoAnnotations.initMocks(this);
        final var route = new EnableNewNomisRoute(nomisBatchService);
        return new RouteBuilder[]{route};
    }

    @Before
    public void mockEndpoints() throws Exception {
        AdviceWith.adviceWith(context, null, a -> {
            a.weaveAddLast().to(MOCK_ENABLE_ENDPOINT);
        });
    }

    @Test
    public void testEnabledNewNomisCamelRoute_callsNomisBatchService() throws Exception {

        template.send(EnableNewNomisRoute.ENABLE_NEW_NOMIS, exchange -> {
        });

        assertMockEndpointsSatisfied();
        final var mockEndpoint = getMockEndpoint(MOCK_ENABLE_ENDPOINT);
        mockEndpoint.assertIsSatisfied();

        verify(nomisBatchService).enableNomis();
    }

}
