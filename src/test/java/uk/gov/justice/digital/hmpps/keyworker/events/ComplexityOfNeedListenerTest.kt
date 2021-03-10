package uk.gov.justice.digital.hmpps.keyworker.events

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import uk.gov.justice.digital.hmpps.keyworker.config.JsonConfig
import uk.gov.justice.digital.hmpps.keyworker.services.ComplexityOfNeedService

@ExtendWith(MockitoExtension::class)
class ComplexityOfNeedListenerTest {
  @Mock
  lateinit var complexityOfNeedService: ComplexityOfNeedService

  lateinit var complexityOfNeedListener: ComplexityOfNeedListener

  @BeforeEach
  fun setUp() {
    complexityOfNeedListener = ComplexityOfNeedListener(complexityOfNeedService, JsonConfig().gson())
  }

  @Test
  fun `should delegate complexity of need changes to the service`() {

    val fileContent = this::class.java.getResource("complexity-of-need-change-to-high.json").readText()

    complexityOfNeedListener.eventListener(fileContent)

    verify(complexityOfNeedService).onComplexityChange("A12345", ComplexityOfNeedLevel.HIGH)
  }
}
