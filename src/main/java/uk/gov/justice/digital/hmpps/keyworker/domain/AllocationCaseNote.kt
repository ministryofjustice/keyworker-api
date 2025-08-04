package uk.gov.justice.digital.hmpps.keyworker.domain

import jakarta.persistence.Embeddable
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Version
import org.hibernate.envers.Audited
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.LocalDateTime
import java.util.UUID

@Audited
@Entity
class AllocationCaseNote(
  val prisonCode: String,
  var personIdentifier: String,
  val staffId: Long,
  val username: String,
  @Embedded
  val caseNoteType: CaseNoteTypeKey,
  val occurredAt: LocalDateTime,
  val createdAt: LocalDateTime,
  @Id
  val id: UUID,
) : CaseNoteType by caseNoteType {
  @Version
  val version: Int? = null
}

interface CaseNoteType {
  val type: String
  val subType: String
}

@Embeddable
data class CaseNoteTypeKey(
  override val type: String,
  override val subType: String,
) : CaseNoteType

interface AllocationCaseNoteRepository : JpaRepository<AllocationCaseNote, UUID> {
  fun deleteAllByPersonIdentifier(personIdentifier: String)

  fun findByPrisonCodeAndCaseNoteTypeInAndOccurredAtBetween(
    prisonCode: String,
    caseNoteTypes: Set<CaseNoteTypeKey>,
    from: LocalDateTime,
    to: LocalDateTime,
  ): List<AllocationCaseNote>

  fun findByStaffIdInAndCaseNoteTypeInAndOccurredAtBetween(
    staffId: Set<Long>,
    caseNoteTypes: Set<CaseNoteTypeKey>,
    from: LocalDateTime,
    to: LocalDateTime,
  ): List<AllocationCaseNote>

  fun findByPrisonCodeAndCaseNoteTypeInAndCreatedAtBetween(
    prisonCode: String,
    caseNoteTypes: Set<CaseNoteTypeKey>,
    from: LocalDateTime,
    to: LocalDateTime,
  ): List<AllocationCaseNote>

  @Query(
    """
    select acn from AllocationCaseNote acn
    where acn.prisonCode = :prisonCode and acn.personIdentifier in :personIdentifiers
    and acn.occurredAt < :before
  """,
  )
  fun findLatestCaseNotesBefore(
    prisonCode: String,
    personIdentifiers: Set<String>,
    caseNoteTypes: Set<CaseNoteTypeKey>,
    before: LocalDateTime,
  ): List<AllocationCaseNote>
}
