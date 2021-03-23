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
import uk.gov.justice.digital.hmpps.keyworker.services.ReconciliationBatchService;

import static org.mockito.Mockito.verify;

@Slf4j
@RunWith(MockitoJUnitRunner.class)
public class KeyworkerReconRouteTest extends CamelTestSupport {

    private final static String MOCK_RECON_ENDPOINT = "mock:recon";

    @Mock
    private ReconciliationBatchService reconciliationBatchService;

    @Override
    public RouteBuilder[] createRouteBuilders() {
        MockitoAnnotations.initMocks(this);
        final var route = new KeyworkerReconRoute(reconciliationBatchService);

        return new RouteBuilder[]{route};
    }

    @Before
    public void mockEndpoints() throws Exception {
        AdviceWith.adviceWith(context, null, a -> {
            a.weaveAddLast().to(MOCK_RECON_ENDPOINT);
        });
    }

    @Test
    public void testReconcileCall_callsReconciliationBatchService() {

        template.send(KeyworkerReconRoute.DIRECT_KEY_WORKER_RECON, exchange -> {
        });

        verify(reconciliationBatchService).reconcileKeyWorkerAllocations();
    }

}
