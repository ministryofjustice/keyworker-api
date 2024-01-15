package uk.gov.justice.digital.hmpps.keyworker.events

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import uk.gov.justice.digital.hmpps.keyworker.config.JsonConfig

@ExtendWith(MockitoExtension::class)
class DomainEventListenerTest {
  @Mock
  lateinit var complexityOfNeedEventProcessor: ComplexityOfNeedEventProcessor

  lateinit var domainEventListener: DomainEventListener

  private val cOMPLEXITYCHANGEEVENT = this::class.java.getResource("complexity-of-need-change-to-high.json").readText()

  @BeforeEach
  fun setUp() {
    domainEventListener = DomainEventListener(complexityOfNeedEventProcessor, JsonConfig().gson())
  }

  @Test
  fun `should delegate complexity of need changes to the service`() {
    domainEventListener.eventListener(cOMPLEXITYCHANGEEVENT)

    verify(
      complexityOfNeedEventProcessor,
    ).onComplexityChange("{\"eventType\": \"complexity-of-need.level.changed\", \"offenderNo\":  \"A12345\",  \"level\":  \"high\"}")
  }
}
