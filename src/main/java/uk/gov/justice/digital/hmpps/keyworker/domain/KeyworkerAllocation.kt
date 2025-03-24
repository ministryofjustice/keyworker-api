package uk.gov.justice.digital.hmpps.keyworker.domain

import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.EntityListeners
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.type.YesNoConverter
import org.springframework.data.annotation.CreatedBy
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import uk.gov.justice.digital.hmpps.keyworker.model.AllocationType
import uk.gov.justice.digital.hmpps.keyworker.model.AllocationTypeConvertor
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "offender_key_worker")
@EntityListeners(AuditingEntityListener::class)
class KeyworkerAllocation(
  @Column(name = "offender_no")
  val personIdentifier: String,
  @Column(name = "prison_id")
  val prisonCode: String,
  @Column(name = "staff_id")
  val staffId: Long,
  @Column(name = "assigned_date_time")
  val assignedAt: LocalDateTime,
  @Column(name = "active_flag")
  @Convert(converter = YesNoConverter::class)
  val active: Boolean,
  @ManyToOne
  @JoinColumn(name = "allocation_reason_id")
  val allocationReason: ReferenceData,
  @Column(name = "alloc_type")
  @Convert(converter = AllocationTypeConvertor::class)
  val allocationType: AllocationType,
  @Column(name = "user_id", nullable = false)
  val userId: String?,
  @Column(name = "expiry_date_time")
  val expiryDateTime: LocalDateTime?,
  @ManyToOne
  @JoinColumn(name = "deallocation_reason_id")
  val deallocationReason: ReferenceData?,
  @Id
  @Column(name = "offender_keyworker_id")
  val id: Long,
) {
  @CreatedDate
  @Column(name = "create_datetime")
  var createdAt: LocalDateTime = LocalDateTime.now()

  @CreatedBy
  @Column(name = "create_user_id", nullable = false)
  var createdBy: String = "SYS"
}

interface KeyworkerAllocationRepository : JpaRepository<KeyworkerAllocation, Long> {
  @Query(
    """
    with allocations as (select ka.offender_keyworker_id as id, ka.offender_no as personIdentifier, ka.assigned_date_time as assignedAt
                     from offender_key_worker ka
                     where ka.prison_id = :prisonCode
                       and ka.alloc_type <> 'P' 
                       and ka.assigned_date_time between :from and :to
    )
    select na.id, na.personIdentifier, na.assignedAt
    from allocations na
    where not exists(select 1
                     from offender_key_worker ka
                     where ka.prison_id = :prisonCode
                       and ka.offender_no = na.personIdentifier 
                       and ka.assigned_date_time < :from
                       and ka.alloc_type <> 'P')  
    """,
    nativeQuery = true,
  )
  fun findNewAllocationsAt(
    prisonCode: String,
    from: LocalDate,
    to: LocalDate,
  ): List<NewAllocation>

  @Query(
    """
    select count(kwa) from KeyworkerAllocation kwa
     where kwa.prisonCode = :prisonCode
     and kwa.personIdentifier in :personIdentifiers
     and kwa.active = true and kwa.allocationType <> 'P'
    """,
  )
  fun countActiveAllocations(
    prisonCode: String,
    personIdentifiers: Set<String>,
  ): Int

  @Query(
    """
      select kwa from KeyworkerAllocation kwa
      where kwa.staffId = :staffId and kwa.prisonCode = :prisonCode
      and kwa.active = true and kwa.allocationType <> 'P'
    """,
  )
  fun findActiveForPrisonStaff(
    prisonCode: String,
    staffId: Long,
  ): List<KeyworkerAllocation>
}

interface NewAllocation {
  val id: UUID
  val personIdentifier: String
  val assignedAt: LocalDateTime
}
