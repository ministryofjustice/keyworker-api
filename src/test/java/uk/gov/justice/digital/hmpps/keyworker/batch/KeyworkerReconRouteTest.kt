package uk.gov.justice.digital.hmpps.keyworker.batch

import groovy.util.logging.Slf4j
import org.apache.camel.Exchange
import org.apache.camel.builder.AdviceWithRouteBuilder
import org.apache.camel.builder.RouteBuilder
import org.apache.camel.model.ProcessorDefinition
import org.apache.camel.test.junit4.CamelTestSupport
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import org.mockito.junit.MockitoJUnitRunner
import uk.gov.justice.digital.hmpps.keyworker.dto.Prison
import uk.gov.justice.digital.hmpps.keyworker.services.PrisonSupportedService
import uk.gov.justice.digital.hmpps.keyworker.services.ReconciliationService
import uk.gov.justice.digital.hmpps.keyworker.services.ReconciliationService.ReconMetrics

@Slf4j
@RunWith(MockitoJUnitRunner::class)
class KeyworkerReconRouteTest : CamelTestSupport() {
    @Mock
    private val prisonSupportedService: PrisonSupportedService? = null

    @Mock
    private val reconciliationService: ReconciliationService? = null
    @Throws(Exception::class)
    public override fun createRouteBuilders(): Array<RouteBuilder> {
        MockitoAnnotations.initMocks(this)
        val route = KeyworkerReconRoute(reconciliationService!!, prisonSupportedService!!)
        return arrayOf(route)
    }

    @Before
    @Throws(Exception::class)
    fun mockEndpoints() {
        context.routeDefinitions[0].adviceWith(context, object : AdviceWithRouteBuilder() {
            @Throws(Exception::class)
            override fun configure() {
                weaveAddLast<ProcessorDefinition<*>>().to(MOCK_PRISONS_ENDPOINT)
            }
        })
        context.routeDefinitions[1].adviceWith(context, object : AdviceWithRouteBuilder() {
            @Throws(Exception::class)
            override fun configure() {
                weaveAddLast<ProcessorDefinition<*>>().to(MOCK_RECON_ENDPOINT)
            }
        })
        context.routeDefinitions[2].adviceWith(context, object : AdviceWithRouteBuilder() {
            @Throws(Exception::class)
            override fun configure() {
                weaveAddLast<ProcessorDefinition<*>>().to(MOCK_DLQ_ENDPOINT)
            }
        })
    }

    @Test
    @Throws(Exception::class)
    fun testGenerateStatsCall() {
        val prisons = java.util.List.of(
                MDI,
                LEI,
                LPI
        )
        `when`(prisonSupportedService!!.migratedPrisons).thenReturn(prisons)
        `when`(reconciliationService!!.reconcileKeyWorkerAllocations(MDI.prisonId)).thenReturn(ReconMetrics(MDI.prisonId, 10, 0))
        `when`(reconciliationService.reconcileKeyWorkerAllocations(LEI.prisonId)).thenReturn(ReconMetrics(LEI.prisonId, 5, 1))
        `when`(reconciliationService.reconcileKeyWorkerAllocations(LPI.prisonId)).thenReturn(ReconMetrics(LPI.prisonId, 3, 2))
        template.send(KeyworkerReconRoute.DIRECT_KEY_WORKER_RECON) { exchange: Exchange? -> }
        assertMockEndpointsSatisfied()
        val mockEndpoint = getMockEndpoint(MOCK_PRISONS_ENDPOINT)
        mockEndpoint.assertIsSatisfied()
        val receivedExchanges = mockEndpoint.receivedExchanges
        assertEquals(1, receivedExchanges.size.toLong())
        val exchangeData: List<Prison> = receivedExchanges[0].`in`.body as List<Prison>
        assertEquals(prisons, exchangeData)
        val mockEndpoint2 = getMockEndpoint(MOCK_RECON_ENDPOINT)
        mockEndpoint2.assertIsSatisfied()
        val receivedExchanges2 = mockEndpoint2.receivedExchanges
        assertEquals(3, receivedExchanges2.size.toLong())
        verify(prisonSupportedService).migratedPrisons
    }

    companion object {
        private const val MOCK_PRISONS_ENDPOINT = "mock:prisons"
        private const val MOCK_RECON_ENDPOINT = "mock:recon"
        private const val MOCK_DLQ_ENDPOINT = "mock:dlq"
        private val MDI = Prison.builder().prisonId("MDI").migrated(true).build()
        private val LEI = Prison.builder().prisonId("LEI").migrated(true).build()
        private val LPI = Prison.builder().prisonId("LPI").migrated(true).build()
    }
}