package uk.gov.justice.digital.hmpps.keyworker.domain

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Version
import org.hibernate.envers.Audited
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDateTime
import java.util.UUID

@Audited
@Entity
class AllocationCaseNote(
  val prisonCode: String,
  var personIdentifier: String,
  val staffId: Long,
  val username: String,
  val type: String,
  val subType: String,
  val occurredAt: LocalDateTime,
  @Id
  val id: UUID,
) {
  @Version
  val version: Int? = null
}

interface AllocationCaseNoteRepository : JpaRepository<AllocationCaseNote, UUID> {
  fun findAllByPersonIdentifier(personIdentifier: String): List<AllocationCaseNote>

  fun deleteAllByPersonIdentifier(personIdentifier: String)
}
