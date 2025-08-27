package uk.gov.justice.digital.hmpps.keyworker.domain

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.Version
import org.hibernate.annotations.TenantId
import org.hibernate.envers.Audited
import org.hibernate.envers.RelationTargetAuditMode.NOT_AUDITED
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

@Audited
@Entity
@Table(name = "recorded_event")
class RecordedEvent(
  val prisonCode: String,
  var personIdentifier: String,
  val staffId: Long,
  val username: String,
  val occurredAt: LocalDateTime,
  val createdAt: LocalDateTime,
  @Audited(targetAuditMode = NOT_AUDITED)
  @ManyToOne
  @JoinColumn(name = "type_id")
  val type: ReferenceData,
  @TenantId
  val policyCode: String,
  @Id
  val id: UUID,
) {
  @Version
  val version: Int? = null
}

interface RecordedEventRepository : JpaRepository<RecordedEvent, UUID> {
  @Transactional
  @Modifying
  @Query("delete from recorded_event re where re.person_identifier = :personIdentifier", nativeQuery = true)
  fun deleteAllByPersonIdentifier(personIdentifier: String)

  @EntityGraph(attributePaths = ["type"])
  fun findByPrisonCodeAndTypeKeyInAndOccurredAtBetween(
    prisonCode: String,
    rdKeys: Set<ReferenceDataKey>,
    from: LocalDateTime,
    to: LocalDateTime,
  ): List<RecordedEvent>

  @EntityGraph(attributePaths = ["type"])
  fun findByStaffIdInAndTypeKeyInAndOccurredAtBetween(
    staffId: Set<Long>,
    rdKeys: Set<ReferenceDataKey>,
    from: LocalDateTime,
    to: LocalDateTime,
  ): List<RecordedEvent>

  @EntityGraph(attributePaths = ["type"])
  fun findByPrisonCodeAndTypeKeyInAndCreatedAtBetween(
    prisonCode: String,
    rdKeys: Set<ReferenceDataKey>,
    from: LocalDateTime,
    to: LocalDateTime,
  ): List<RecordedEvent>

  @Query(
    """
      with latest as (
        select acn.id as id, row_number() over (partition by acn.personIdentifier order by acn.occurredAt desc) as row
        from RecordedEvent acn
        where acn.prisonCode = :prisonCode and acn.personIdentifier in :personIdentifiers and acn.type.key = :rdKey
        and acn.occurredAt < :before
    )
    select re 
    from RecordedEvent re
    join fetch re.type t
    join latest l on l.id = re.id and l.row = 1
  """,
  )
  fun findLatestRecordedEventBefore(
    prisonCode: String,
    personIdentifiers: Set<String>,
    rdKey: ReferenceDataKey,
    before: LocalDateTime,
  ): List<RecordedEvent>

  @Query(
    """
      with latest as (
        select acn.id as id, row_number() over (partition by acn.type.key.code order by acn.occurredAt desc) as row
        from RecordedEvent acn
        where acn.personIdentifier = :personIdentifier and acn.type.key in :rdKeys
    )
    select re 
    from RecordedEvent re
    join fetch re.type t
    join latest l on l.id = re.id and l.row = 1
  """,
  )
  fun findLatestRecordedEvents(
    personIdentifier: String,
    rdKeys: Set<ReferenceDataKey>,
  ): List<RecordedEvent>

  @Query("select re.policy_code from recorded_event re where re.id = :id", nativeQuery = true)
  fun findPolicyForId(id: UUID): String?
}
