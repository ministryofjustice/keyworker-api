package uk.gov.justice.digital.hmpps.keyworker.model.staff

import uk.gov.justice.digital.hmpps.keyworker.config.AllocationPolicy

data class JobClassificationResponse(
  val policies: Set<AllocationPolicy>,
)
