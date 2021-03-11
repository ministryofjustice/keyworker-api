package uk.gov.justice.digital.hmpps.keyworker.events

import com.fasterxml.jackson.annotation.JsonProperty

enum class ComplexityOfNeedLevel {
  @JsonProperty("high")
  HIGH,
  @JsonProperty("low")
  LOW,
  @JsonProperty("medium")
  MEDIUM
}
