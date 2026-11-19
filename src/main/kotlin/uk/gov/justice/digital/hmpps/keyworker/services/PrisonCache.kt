package uk.gov.justice.digital.hmpps.keyworker.services

import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import uk.gov.justice.digital.hmpps.keyworker.domain.CachedPrison
import uk.gov.justice.digital.hmpps.keyworker.domain.PrisonRepository
import uk.gov.justice.digital.hmpps.keyworker.integration.events.domain.HmppsDomainEvent
import uk.gov.justice.digital.hmpps.keyworker.integration.events.domain.PrisonInformation
import uk.gov.justice.digital.hmpps.keyworker.integration.prisonregister.PrisonRegisterClient

@Service
class PrisonCache(
  private val transactionTemplate: TransactionTemplate,
  private val prisonRepository: PrisonRepository,
  private val prisonRegister: PrisonRegisterClient,
) {
  fun mergePrison(de: HmppsDomainEvent<PrisonInformation>) {
    val prison = prisonRegister.getPrison(de.additionalInformation.prisonCode)
    transactionTemplate.executeWithoutResult {
      prisonRepository.findByIdOrNull(prison.prisonId)?.also { it.name = prison.prisonName }
        ?: prisonRepository.save(CachedPrison(prison.prisonId, prison.prisonName))
    }
  }

  fun reconcileAll() {
    val prisons = prisonRegister.getAllPrisons()
    transactionTemplate.executeWithoutResult {
      val cached = prisonRepository.findAll().associateBy { it.code }
      prisonRepository.saveAll(prisons.map { cached[it.prisonId] ?: it.newPrison() })
    }
  }

  fun findByCodes(codes: Collection<String>): List<Prison> =
    prisonRepository
      .findAllById(codes)
      .map { Prison(it.code, it.name) }

  fun findByCode(code: String): Prison? =
    prisonRepository
      .findByIdOrNull(code)
      ?.let { Prison(it.code, it.name) }
}

private fun Prison.newPrison() = CachedPrison(prisonId, prisonName)
