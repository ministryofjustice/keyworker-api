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
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import org.mockito.junit.MockitoJUnitRunner
import uk.gov.justice.digital.hmpps.keyworker.services.KeyworkerBatchService
import java.util.List

@Slf4j
@RunWith(MockitoJUnitRunner::class)
class UpdateStatusRouteTest : CamelTestSupport() {
    @Mock
    private val service: KeyworkerBatchService? = null
    @Throws(Exception::class)
    public override fun createRouteBuilders(): Array<RouteBuilder> {
        MockitoAnnotations.initMocks(this)
        val updateStatusRoute = UpdateStatusRoute(service!!)
        return arrayOf(updateStatusRoute)
    }

    @Before
    @Throws(Exception::class)
    fun mockEndpoints() {
        context.routeDefinitions[0].adviceWith(context, object : AdviceWithRouteBuilder() {
            @Throws(Exception::class)
            override fun configure() {
                weaveAddLast<ProcessorDefinition<*>>().to("mock:result")
            }
        })
    }

    @Test
    @Throws(Exception::class)
    fun testUpdateStatus() {
        Mockito.`when`(service!!.executeUpdateStatus()).thenReturn(List.of(8L, 9L))
        template.send(UpdateStatusRoute.DIRECT_UPDATE_STATUS) { exchange: Exchange? -> }
        assertMockEndpointsSatisfied()
        val mockEndpoint = getMockEndpoint(SUBMIT_ENDPOINT)
        mockEndpoint.assertIsSatisfied()
        val receivedExchanges = mockEndpoint.receivedExchanges
        assertEquals(1, receivedExchanges.size.toLong())
        val appData = receivedExchanges[0].getIn().getBody(String::class.java)
        assertEquals(APP_DATA, appData)
        Mockito.verify(service).executeUpdateStatus()
    }

    companion object {
        private const val APP_DATA = "[8, 9]"
        private const val SUBMIT_ENDPOINT = "mock:result"
    }
}