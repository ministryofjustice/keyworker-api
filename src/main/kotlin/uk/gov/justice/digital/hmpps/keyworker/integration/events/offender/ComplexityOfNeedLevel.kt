package uk.gov.justice.digital.hmpps.keyworker.integration.events.offender

import com.fasterxml.jackson.annotation.JsonProperty

enum class ComplexityOfNeedLevel {
  @JsonProperty("high")
  HIGH,

  @JsonProperty("low")
  LOW,

  @JsonProperty("medium")
  MEDIUM,
}
