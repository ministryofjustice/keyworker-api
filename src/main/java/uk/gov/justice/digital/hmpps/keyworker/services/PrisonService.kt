package uk.gov.justice.digital.hmpps.keyworker.services

import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.keyworker.dto.PrisonKeyworkerStatus
import uk.gov.justice.digital.hmpps.keyworker.dto.PrisonKeyworkerStatus.Companion.NOT_CONFIGURED
import uk.gov.justice.digital.hmpps.keyworker.model.PrisonSupported
import uk.gov.justice.digital.hmpps.keyworker.repository.PrisonSupportedRepository

@Service
class PrisonService(
  val prisonSupported: PrisonSupportedRepository,
) {
  fun getPrisonKeyworkerStatus(prisonCode: String) = prisonSupported.findByIdOrNull(prisonCode).keyworkerStatus()
}

private fun PrisonSupported?.keyworkerStatus(): PrisonKeyworkerStatus =
  this?.let { PrisonKeyworkerStatus(it.isMigrated, it.hasPrisonersWithHighComplexityNeeds()) } ?: NOT_CONFIGURED
