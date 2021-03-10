package uk.gov.justice.digital.hmpps.keyworker.services

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.keyworker.events.ComplexityOfNeedLevel

@Service
class ComplexityOfNeedService(val keyworkerService: KeyworkerService) {
  companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }

  fun onComplexityChange(offenderNo: String, level: ComplexityOfNeedLevel) {
    if (level != ComplexityOfNeedLevel.HIGH) return

    log.info("Deallocating an offender based on their HIGH complexity of need")

    keyworkerService.deallocate(offenderNo)
  }
}
