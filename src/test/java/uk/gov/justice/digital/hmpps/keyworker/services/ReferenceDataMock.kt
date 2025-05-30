package uk.gov.justice.digital.hmpps.keyworker.services

import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceData
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceDataDomain
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceDataKey
import uk.gov.justice.digital.hmpps.keyworker.model.StaffStatus

object ReferenceDataMock {
  @JvmStatic
  val keyworkerStatuses: Map<String, ReferenceData> =
    StaffStatus.entries
      .map {
        ReferenceData(
          ReferenceDataKey(ReferenceDataDomain.STAFF_STATUS, it.name),
          "Description of ${it.name}",
          it.ordinal,
          it.ordinal.toLong(),
        )
      }.associateBy { it.code }
}
