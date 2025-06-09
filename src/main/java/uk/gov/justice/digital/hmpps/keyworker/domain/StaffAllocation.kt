package uk.gov.justice.digital.hmpps.keyworker.domain

import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.EntityListeners
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.SQLRestriction
import org.hibernate.type.YesNoConverter
import org.springframework.data.annotation.CreatedBy
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedBy
import org.springframework.data.annotation.LastModifiedDate
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
@SQLRestriction("alloc_type <> 'P'")
class StaffAllocation(
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
  var active: Boolean,
  @ManyToOne
  @JoinColumn(name = "allocation_reason_id")
  val allocationReason: ReferenceData,
  @Column(name = "alloc_type")
  @Convert(converter = AllocationTypeConvertor::class)
  val allocationType: AllocationType,
  @Column(name = "user_id")
  val allocatedBy: String,
  @Column(name = "expiry_date_time")
  var expiryDateTime: LocalDateTime?,
  @ManyToOne
  @JoinColumn(name = "deallocation_reason_id")
  var deallocationReason: ReferenceData?,
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "offender_keyworker_id")
  val id: Long?,
) {
  @CreatedDate
  @Column(name = "create_datetime")
  var createdAt: LocalDateTime = LocalDateTime.now()

  @CreatedBy
  @Column(name = "create_user_id", nullable = false)
  var createdBy: String = "SYS"

  @LastModifiedDate
  @Column(name = "modify_datetime")
  var lastModifiedAt: LocalDateTime? = null

  @LastModifiedBy
  @Column(name = "modify_user_id")
  var lastModifiedBy: String? = null

  fun deallocate(deallocationReason: ReferenceData) {
    this.active = false
    this.expiryDateTime = LocalDateTime.now()
    this.deallocationReason = deallocationReason
  }
}

interface StaffAllocationRepository : JpaRepository<StaffAllocation, Long> {
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
    select count(sa) from StaffAllocation sa
     where sa.prisonCode = :prisonCode
     and sa.personIdentifier in :personIdentifiers
     and sa.active = true and sa.allocationType <> 'P'
    """,
  )
  fun countActiveAllocations(
    prisonCode: String,
    personIdentifiers: Set<String>,
  ): Int

  @Query(
    """
      select sa from StaffAllocation sa
      where sa.staffId = :staffId and sa.prisonCode = :prisonCode
      and sa.active = true
    """,
  )
  fun findActiveForPrisonStaff(
    prisonCode: String,
    staffId: Long,
  ): List<StaffAllocation>

  @Query(
    """
      select sa from StaffAllocation sa
      where sa.staffId = :staffId and sa.prisonCode = :prisonCode
      and sa.assignedAt <= :toDate and (sa.expiryDateTime is null or sa.expiryDateTime >= :fromDate)
    """,
  )
  fun findActiveForPrisonStaffBetween(
    prisonCode: String,
    staffId: Long,
    fromDate: LocalDateTime,
    toDate: LocalDateTime,
  ): List<StaffAllocation>

  fun findAllByPersonIdentifierInAndActiveTrue(personIdentifiers: Set<String>): List<StaffAllocation>

  fun findAllByPersonIdentifier(personIdentifier: String): List<StaffAllocation>

  @Query(
    """
    with summary as (
        select sa.personIdentifier as pi, sum(case when sa.active then 1 else 0 end) as active, count(sa) as count
        from StaffAllocation sa
        where sa.personIdentifier in :personIdentifiers 
        and sa.prisonCode = :prisonCode
        group by sa.personIdentifier
    )
    select sum.pi as personIdentifier, sum.active as activeCount, sum.count as totalCount, cur.staffId as staffId
    from summary sum
    left join StaffAllocation cur on cur.personIdentifier = sum.pi and cur.active = true
    """,
  )
  fun summariesFor(
    prisonCode: String,
    personIdentifiers: Set<String>,
  ): List<AllocationSummary>

  fun findFirstByPersonIdentifierAndActiveIsTrueOrderByAssignedAtDesc(personIdentifier: String): StaffAllocation?

  @Query(
    """
    with auto_alloc as ( 
        select ka.*, row_number() over(partition by ka.staff_id order by ka.assigned_date_time desc) as row_number from offender_key_worker ka 
        where ka.staff_id in :staffIds and ka.alloc_type = 'A' and ka.active_flag = 'Y'
    )
    select aa.* from auto_alloc aa
    where aa.row_number = 1
    """,
    nativeQuery = true,
  )
  fun findLatestAutoAllocationsFor(staffIds: Set<Long>): List<StaffAllocation>

  @Query(
    """
      select sa.staffId from StaffAllocation sa
      where sa.personIdentifier = :personIdentifier and sa.prisonCode = :prisonCode
      and sa.staffId in :staffIds
    """,
  )
  fun findPreviousAllocations(
    prisonCode: String,
    personIdentifier: String,
    staffIds: Set<Long>,
  ): Set<Long>
}

interface NewAllocation {
  val id: UUID
  val personIdentifier: String
  val assignedAt: LocalDateTime
}

interface AllocationSummary {
  val personIdentifier: String
  val activeCount: Int
  val totalCount: Int
  val staffId: Long?
}
