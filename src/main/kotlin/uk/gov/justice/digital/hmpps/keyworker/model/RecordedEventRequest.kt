package uk.gov.justice.digital.hmpps.keyworker.model

import uk.gov.justice.digital.hmpps.keyworker.model.staff.RecordedEventType
import java.time.LocalDate

data class RecordedEventRequest(
  val types: Set<RecordedEventType>,
  val from: LocalDate?,
  val to: LocalDate?,
)
