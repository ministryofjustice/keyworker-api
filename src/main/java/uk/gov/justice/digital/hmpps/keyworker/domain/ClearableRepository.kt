package uk.gov.justice.digital.hmpps.keyworker.domain

import jakarta.persistence.EntityManager
import org.springframework.transaction.annotation.Transactional

interface ClearableRepository {
  fun clear()
}

@Transactional
class ClearableRepositoryImpl(
  private val entityManager: EntityManager,
) : ClearableRepository {
  override fun clear() {
    entityManager.clear()
  }
}
