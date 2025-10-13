package uk.gov.justice.digital.hmpps.keyworker.domain

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.Version
import org.hibernate.annotations.Fetch
import org.hibernate.annotations.FetchMode
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
  @Fetch(FetchMode.JOIN)
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
  fun findByPrisonCodeAndOccurredAtBetween(
    prisonCode: String,
    from: LocalDateTime,
    to: LocalDateTime,
  ): List<RecordedEvent>

  @Query(
    """
   select re from RecordedEvent re
   join fetch re.type t
   where re.staffId in :staffIds
   and re.occurredAt between :from and :to
   and re.prisonCode = :prisonCode
   and re.personIdentifier in (
    select a.personIdentifier from Allocation a 
    where a.staffId = re.staffId
    and a.prisonCode = :prisonCode
    and a.allocatedAt <= :to
    and (a.deallocatedAt is null or a.deallocatedAt >= :from)
    )
 """,
  )
  fun findByStaffIdInAndOccurredAtBetween(
    prisonCode: String,
    staffIds: Set<Long>,
    from: LocalDateTime,
    to: LocalDateTime,
  ): List<RecordedEvent>

  @EntityGraph(attributePaths = ["type"])
  fun findByPrisonCodeAndCreatedAtBetween(
    prisonCode: String,
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
        select lre.id as id, row_number() over (partition by lre.type_id, lre.policy_code order by lre.occurred_at desc) as row
        from recorded_event lre
        where lre.person_identifier = :personIdentifier and lre.policy_code in :policies
    )
    select 
        re.prison_code as prisonCode, 
        re.staff_id as staffId, 
        re.username as username, 
        re.occurred_at as occurredAt, 
        type.code as typeCode,
        re.policy_code as policyCode
    from recorded_event re
    join reference_data type on re.type_id = type.id
    join latest l on l.id = re.id and l.row = 1
  """,
    nativeQuery = true,
  )
  fun findLatestRecordedEvents(
    personIdentifier: String,
    policies: Set<String>,
  ): List<LatestRecordedEvent>

  @Query("select re.policy_code from recorded_event re where re.id = :id", nativeQuery = true)
  fun findPolicyForId(id: UUID): String?
}

interface LatestRecordedEvent {
  val prisonCode: String
  val staffId: Long
  val username: String
  val occurredAt: LocalDateTime
  val typeCode: String
  val policyCode: String
}
