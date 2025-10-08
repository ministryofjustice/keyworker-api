package uk.gov.justice.digital.hmpps.keyworker.services

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceDataDomain
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceDataRepository
import uk.gov.justice.digital.hmpps.keyworker.model.CodedDescription

@Transactional(readOnly = true)
@Service
class RetrieveReferenceData(
  private val repository: ReferenceDataRepository,
) {
  fun findAllByDomain(domain: ReferenceDataDomain): List<CodedDescription> =
    repository.findByKeyDomainOrderBySequenceNumber(domain).map { CodedDescription(it.code, it.description()) }
}
