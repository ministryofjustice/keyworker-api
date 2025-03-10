package uk.gov.justice.digital.hmpps.keyworker.services

import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceData
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceDataDomain
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceDataKey
import uk.gov.justice.digital.hmpps.keyworker.model.KeyworkerStatus

object ReferenceDataMock {
  @JvmStatic
  val keyworkerStatuses: Map<String, ReferenceData> =
    KeyworkerStatus.entries
      .map {
        ReferenceData(
          ReferenceDataKey(ReferenceDataDomain.KEYWORKER_STATUS, it.name),
          "Description of ${it.name}",
          it.ordinal,
          it.ordinal.toLong(),
        )
      }.associateBy { it.code }
}
