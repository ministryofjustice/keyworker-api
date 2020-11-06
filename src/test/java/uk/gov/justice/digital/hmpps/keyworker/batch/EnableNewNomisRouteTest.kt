package uk.gov.justice.digital.hmpps.keyworker.batch

import com.microsoft.applicationinsights.TelemetryClient
import org.apache.camel.Exchange
import org.apache.camel.builder.AdviceWithRouteBuilder
import org.apache.camel.builder.RouteBuilder
import org.apache.camel.model.ProcessorDefinition
import org.apache.camel.test.junit4.CamelTestSupport
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.isA
import org.mockito.ArgumentMatchers.isNull
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.Mockito.times
import org.mockito.MockitoAnnotations
import org.mockito.junit.MockitoJUnitRunner
import uk.gov.justice.digital.hmpps.keyworker.dto.CaseloadUpdate
import uk.gov.justice.digital.hmpps.keyworker.dto.Prison
import uk.gov.justice.digital.hmpps.keyworker.services.NomisService

@RunWith(MockitoJUnitRunner::class)
class EnableNewNomisRouteTest : CamelTestSupport() {
    @Mock
    private lateinit var nomisService: NomisService

    @Mock
    private lateinit var telemetryClient: TelemetryClient

    public override fun createRouteBuilders(): Array<RouteBuilder> {
        MockitoAnnotations.initMocks(this)
        val route = EnableNewNomisRoute(nomisService, telemetryClient)
        return arrayOf(route)
    }

    @Before
    @Throws(Exception::class)
    fun mockEndpoints() {
        context.routeDefinitions[0].adviceWith(context, object : AdviceWithRouteBuilder() {
            override fun configure() {
                weaveAddLast<ProcessorDefinition<*>>().to(MOCK_PRISONS_ENDPOINT)
            }
        })
        context.routeDefinitions[1].adviceWith(context, object : AdviceWithRouteBuilder() {
            override fun configure() {
                weaveAddLast<ProcessorDefinition<*>>().to(MOCK_ENABLE_ENDPOINT)
            }
        })
        context.routeDefinitions[2].adviceWith(context, object : AdviceWithRouteBuilder() {
            override fun configure() {
                weaveAddLast<ProcessorDefinition<*>>().to(MOCK_DLQ_ENDPOINT)
            }
        })
    }

    @Test
    @Throws(Exception::class)
    fun testEnabledNewNomisCamelRoute() {
        val prisons = java.util.List.of(MDI, LEI, LPI)
        `when`(nomisService.allPrisons).thenReturn(prisons)
        val MDIResponse = CaseloadUpdate(MDI.getPrisonId(), 2)
        `when`(nomisService.enableNewNomisForCaseload(eq(MDI.getPrisonId()))).thenReturn(MDIResponse)
        val LEIResponse = CaseloadUpdate(LEI.getPrisonId(), 0)
        `when`(nomisService.enableNewNomisForCaseload(eq(LEI.getPrisonId()))).thenReturn(LEIResponse)
        val LPIResponse = CaseloadUpdate(LPI.getPrisonId(), 14)
        `when`(nomisService.enableNewNomisForCaseload(eq(LPI.getPrisonId()))).thenReturn(LPIResponse)
        template.send(EnableNewNomisRoute.ENABLE_NEW_NOMIS) { _: Exchange? -> }
        assertMockEndpointsSatisfied()
        val mockEndpoint = getMockEndpoint(MOCK_PRISONS_ENDPOINT)
        mockEndpoint.assertIsSatisfied()
        val receivedExchanges = mockEndpoint.receivedExchanges
        assertEquals(1, receivedExchanges.size.toLong())
        val exchangeData: List<Prison> = receivedExchanges[0].`in`.body as List<Prison>
        assertEquals(prisons, exchangeData)
        val mockEndpoint2 = getMockEndpoint(MOCK_ENABLE_ENDPOINT)
        mockEndpoint2.assertIsSatisfied()
        val receivedExchanges2 = mockEndpoint2.receivedExchanges
        assertEquals(3, receivedExchanges2.size.toLong())
        assertEquals(receivedExchanges2[0].getIn().getBody(CaseloadUpdate::class.java), MDIResponse)
        assertEquals(receivedExchanges2[1].getIn().getBody(CaseloadUpdate::class.java), LEIResponse)
        assertEquals(receivedExchanges2[2].getIn().getBody(CaseloadUpdate::class.java), LPIResponse)
        verify(nomisService).allPrisons
        verify(nomisService).enableNewNomisForCaseload(eq(MDI.getPrisonId()))
        verify(nomisService).enableNewNomisForCaseload(eq(LEI.getPrisonId()))
        verify(nomisService).enableNewNomisForCaseload(eq(LPI.getPrisonId()))
        verify(telemetryClient, times(2)).trackEvent(eq("ApiUsersEnabled"), isA(MutableMap::class.java) as MutableMap<String, String>?, isNull())
    }

    companion object {
        private const val MOCK_PRISONS_ENDPOINT = "mock:getAllPrisons"
        private const val MOCK_ENABLE_ENDPOINT = "mock:enableNewNomis"
        private const val MOCK_DLQ_ENDPOINT = "mock:dlq"
        private val MDI: Prison = Prison.builder().prisonId("MDI").build()
        private val LEI: Prison = Prison.builder().prisonId("LEI").build()
        private val LPI: Prison = Prison.builder().prisonId("LPI").build()
    }
}
