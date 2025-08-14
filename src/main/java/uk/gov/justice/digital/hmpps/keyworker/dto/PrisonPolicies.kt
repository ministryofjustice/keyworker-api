package uk.gov.justice.digital.hmpps.keyworker.dto

import uk.gov.justice.digital.hmpps.keyworker.config.AllocationPolicy

data class PrisonPolicies(
  val policies: Set<PolicyEnabled>,
)

data class PolicyEnabled(
  val policy: AllocationPolicy,
  val enabled: Boolean,
)
