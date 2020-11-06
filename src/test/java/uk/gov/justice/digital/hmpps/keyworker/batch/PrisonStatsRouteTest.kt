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
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import org.mockito.junit.MockitoJUnitRunner
import uk.gov.justice.digital.hmpps.keyworker.dto.Prison
import uk.gov.justice.digital.hmpps.keyworker.model.PrisonKeyWorkerStatistic
import uk.gov.justice.digital.hmpps.keyworker.services.KeyworkerStatsService
import uk.gov.justice.digital.hmpps.keyworker.services.PrisonSupportedService
import java.time.LocalDate

@Slf4j
@RunWith(MockitoJUnitRunner::class)
class PrisonStatsRouteTest : CamelTestSupport() {
    @Mock
    private val prisonSupportedService: PrisonSupportedService? = null

    @Mock
    private val keyworkerStatsService: KeyworkerStatsService? = null
    @Throws(Exception::class)
    public override fun createRouteBuilders(): Array<RouteBuilder> {
        MockitoAnnotations.initMocks(this)
        val route = PrisonStatsRoute(keyworkerStatsService!!, prisonSupportedService!!)
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
                weaveAddLast<ProcessorDefinition<*>>().to(MOCK_GENSTATS_ENDPOINT)
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
        Mockito.`when`(prisonSupportedService!!.migratedPrisons).thenReturn(prisons)
        val now = LocalDate.now()
        val mdiStats = PrisonKeyWorkerStatistic.builder().prisonId(MDI.prisonId).snapshotDate(now).build()
        Mockito.`when`(keyworkerStatsService!!.generatePrisonStats(MDI.prisonId)).thenReturn(mdiStats)
        val leiStats = PrisonKeyWorkerStatistic.builder().prisonId(LEI.prisonId).snapshotDate(now).build()
        Mockito.`when`(keyworkerStatsService.generatePrisonStats(LEI.prisonId)).thenReturn(leiStats)
        val lpiStats = PrisonKeyWorkerStatistic.builder().prisonId(LPI.prisonId).snapshotDate(now).build()
        Mockito.`when`(keyworkerStatsService.generatePrisonStats(LPI.prisonId)).thenReturn(lpiStats)
        template.send(PrisonStatsRoute.DIRECT_PRISON_STATS) { exchange: Exchange? -> }
        assertMockEndpointsSatisfied()
        val mockEndpoint = getMockEndpoint(MOCK_PRISONS_ENDPOINT)
        mockEndpoint.assertIsSatisfied()
        val receivedExchanges = mockEndpoint.receivedExchanges
        assertEquals(1, receivedExchanges.size.toLong())
        val exchangeData: List<Prison> = receivedExchanges[0].`in`.body as List<Prison>
        assertEquals(prisons, exchangeData)
        val mockEndpoint2 = getMockEndpoint(MOCK_GENSTATS_ENDPOINT)
        mockEndpoint2.assertIsSatisfied()
        val receivedExchanges2 = mockEndpoint2.receivedExchanges
        assertEquals(3, receivedExchanges2.size.toLong())
        assertEquals(mdiStats, receivedExchanges2[0].getIn().getBody(PrisonKeyWorkerStatistic::class.java))
        assertEquals(leiStats, receivedExchanges2[1].getIn().getBody(PrisonKeyWorkerStatistic::class.java))
        assertEquals(lpiStats, receivedExchanges2[2].getIn().getBody(PrisonKeyWorkerStatistic::class.java))
        Mockito.verify(prisonSupportedService).migratedPrisons
        Mockito.verify(keyworkerStatsService, Mockito.times(3)).generatePrisonStats(ArgumentMatchers.isA(String::class.java))
    }

    @Test
    @Throws(Exception::class)
    fun testGenerateStatsCallError() {
        val prisons = java.util.List.of(
                MDI,
                LEI,
                LPI
        )
        Mockito.`when`(prisonSupportedService!!.migratedPrisons).thenReturn(prisons)
        Mockito.`when`(keyworkerStatsService!!.generatePrisonStats(MDI.prisonId)).thenThrow(NullPointerException::class.java)
        Mockito.`when`(keyworkerStatsService.generatePrisonStats(LEI.prisonId)).thenReturn(PrisonKeyWorkerStatistic.builder().prisonId(LEI.prisonId).build())
        Mockito.`when`(keyworkerStatsService.generatePrisonStats(LPI.prisonId)).thenReturn(PrisonKeyWorkerStatistic.builder().prisonId(LPI.prisonId).build())
        template.send(PrisonStatsRoute.DIRECT_PRISON_STATS) { exchange: Exchange? -> }
        assertMockEndpointsSatisfied()
        val statsEndpoint = getMockEndpoint(MOCK_GENSTATS_ENDPOINT)
        statsEndpoint.assertIsSatisfied()
        val receivedExchanges = statsEndpoint.receivedExchanges
        assertEquals(2, receivedExchanges.size.toLong())
        val dlqEndpoint = getMockEndpoint(MOCK_DLQ_ENDPOINT)
        dlqEndpoint.assertIsSatisfied()
        val dlqExchanges = dlqEndpoint.receivedExchanges
        assertEquals(1, dlqExchanges.size.toLong())
        Mockito.verify(prisonSupportedService).migratedPrisons
        Mockito.verify(keyworkerStatsService, Mockito.times(5)).generatePrisonStats(ArgumentMatchers.isA(String::class.java))
        Mockito.verify(keyworkerStatsService).raiseStatsProcessingError(ArgumentMatchers.eq(MDI.prisonId), ArgumentMatchers.isA(Exchange::class.java))
    }

    companion object {
        private const val MOCK_PRISONS_ENDPOINT = "mock:prisons"
        private const val MOCK_GENSTATS_ENDPOINT = "mock:gen-stats"
        private const val MOCK_DLQ_ENDPOINT = "mock:dlq"
        private val MDI = Prison.builder().prisonId("MDI").migrated(true).build()
        private val LEI = Prison.builder().prisonId("LEI").migrated(true).build()
        private val LPI = Prison.builder().prisonId("LPI").migrated(true).build()
    }
}