package uk.gov.justice.digital.hmpps.keyworker.dto

import uk.gov.justice.digital.hmpps.keyworker.model.KeyworkerStatus
import java.time.LocalDate

data class KeyworkerConfigRequest(
  val status: KeyworkerStatus,
  val capacity: Int,
  val deactivateActiveAllocations: Boolean,
  val removeFromAutoAllocation: Boolean,
  val reactivateOn: LocalDate?,
)
