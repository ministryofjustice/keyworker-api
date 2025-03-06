package uk.gov.justice.digital.hmpps.keyworker.domain

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Version
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDateTime
import java.util.UUID

@Entity
class KeyworkerEntry(
  override var occurredAt: LocalDateTime,
  override var personIdentifier: String,
  override val staffId: Long,
  override val staffUsername: String,
  override val prisonCode: String,
  override val createdAt: LocalDateTime,
  override var textLength: Int,
  override var amendmentCount: Int,
  @Id
  override val id: UUID,
) : KeyworkerInteraction {
  @Version
  val version: Int? = null
}

interface KeyworkerEntryRepository : JpaRepository<KeyworkerEntry, UUID>
