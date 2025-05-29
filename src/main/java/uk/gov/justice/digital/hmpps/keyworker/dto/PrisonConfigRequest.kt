package uk.gov.justice.digital.hmpps.keyworker.dto

import jakarta.validation.constraints.Min

data class PrisonConfigRequest(
  val isEnabled: Boolean,
  val allowAutoAllocation: Boolean,
  @field:Min(1, "capacity must be greater than 0") val capacity: Int,
  @field:Min(1, "maximum capacity must be greater than 0") val maximumCapacity: Int,
  @field:Min(1, "frequency in weeks must be greater than 0") val frequencyInWeeks: Int,
  val hasPrisonersWithHighComplexityNeeds: Boolean?,
)

data class PrisonConfigResponse(
  val isEnabled: Boolean,
  val hasPrisonersWithHighComplexityNeeds: Boolean,
  val allowAutoAllocation: Boolean,
  val capacity: Int,
  val maximumCapacity: Int,
  val frequencyInWeeks: Int,
)
