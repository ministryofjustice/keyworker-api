package uk.gov.justice.digital.hmpps.keyworker.domain

import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.SQLRestriction
import org.hibernate.annotations.TenantId
import org.hibernate.envers.Audited
import org.hibernate.envers.RelationTargetAuditMode.NOT_AUDITED
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationContext
import uk.gov.justice.digital.hmpps.keyworker.dto.ReportingPeriod
import uk.gov.justice.digital.hmpps.keyworker.model.AllocationType
import uk.gov.justice.digital.hmpps.keyworker.model.AllocationTypeConvertor
import uk.gov.justice.digital.hmpps.keyworker.utils.IdGenerator.newUuid
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@Audited
@Entity
@Table(name = "allocation")
@SQLRestriction("allocation_type <> 'P'")
class Allocation(
  @Audited(withModifiedFlag = true)
  @Column(name = "person_identifier")
  val personIdentifier: String,
  @Column(name = "prison_code")
  val prisonCode: String,
  @Column(name = "staff_id")
  val staffId: Long,
  @Column(name = "allocated_at")
  val allocatedAt: LocalDateTime,
  @Audited(withModifiedFlag = true, modifiedColumnName = "is_active_modified")
  @Column(name = "is_active")
  var isActive: Boolean,
  @Audited(targetAuditMode = NOT_AUDITED)
  @ManyToOne
  @JoinColumn(name = "allocation_reason_id")
  val allocationReason: ReferenceData,
  @Column(name = "allocation_type")
  @Convert(converter = AllocationTypeConvertor::class)
  val allocationType: AllocationType,
  @Column(name = "allocated_by")
  val allocatedBy: String,
  @Audited(withModifiedFlag = true)
  @Column(name = "deallocated_at")
  var deallocatedAt: LocalDateTime?,
  @Audited(targetAuditMode = NOT_AUDITED, withModifiedFlag = true)
  @ManyToOne
  @JoinColumn(name = "deallocation_reason_id")
  var deallocationReason: ReferenceData?,
  @Audited(withModifiedFlag = true)
  @Column(name = "deallocated_by")
  var deallocatedBy: String?,
  @TenantId
  @Column(name = "policy_code", updatable = false)
  val policy: String = AllocationContext.get().policy.name,
  @Id
  @Column(name = "id")
  val id: UUID = newUuid(),
) {
  fun deallocate(deallocationReason: ReferenceData) {
    val context = AllocationContext.get()
    this.isActive = false
    this.deallocatedAt = context.requestAt
    this.deallocatedBy = context.username
    this.deallocationReason = deallocationReason
  }
}

fun List<Allocation>.filterApplicable(reportingPeriod: ReportingPeriod) =
  filter {
    (it.deallocatedAt == null || !it.deallocatedAt!!.isBefore(reportingPeriod.from)) &&
      it.allocatedAt.isBefore(reportingPeriod.to)
  }

interface StaffAllocationRepository : JpaRepository<Allocation, UUID> {
  @Query(
    """
    with allocations as (select a.id as id, a.person_identifier as personIdentifier, a.allocated_at as assignedAt
                     from allocation a
                     where a.prison_code = :prisonCode
                       and a.allocation_type <> 'P' 
                       and a.allocated_at between :from and :to
    )
    select na.id, na.personIdentifier, na.assignedAt
    from allocations na
    where not exists(select 1
                     from allocation a
                     where a.prison_code = :prisonCode
                       and a.person_identifier = na.personIdentifier 
                       and a.allocated_at < :from
                       and a.allocation_type <> 'P')  
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
     select count(sa) from Allocation sa
     where sa.prisonCode = :prisonCode
     and sa.personIdentifier in :personIdentifiers
     and sa.isActive = true
    """,
  )
  fun countActiveAllocations(
    prisonCode: String,
    personIdentifiers: Set<String>,
  ): Int

  @Query(
    """
      select sa from Allocation sa
      where sa.staffId = :staffId and sa.prisonCode = :prisonCode
      and sa.isActive = true
    """,
  )
  fun findActiveForPrisonStaff(
    prisonCode: String,
    staffId: Long,
  ): List<Allocation>

  @Query(
    """
      select sa from Allocation sa
      where sa.staffId = :staffId and sa.prisonCode = :prisonCode
      and sa.allocatedAt <= :toDate and (sa.deallocatedAt is null or sa.deallocatedAt >= :fromDate)
    """,
  )
  fun findActiveForPrisonStaffBetween(
    prisonCode: String,
    staffId: Long,
    fromDate: LocalDateTime,
    toDate: LocalDateTime,
  ): List<Allocation>

  @Query(
    """
      select sa from Allocation sa
      where sa.staffId in :staffIds and sa.prisonCode = :prisonCode
      and sa.allocatedAt <= :toDate and (sa.deallocatedAt is null or sa.deallocatedAt >= :fromDate)
    """,
  )
  fun findActiveForPrisonStaffBetween(
    prisonCode: String,
    staffIds: Set<Long>,
    fromDate: LocalDateTime,
    toDate: LocalDateTime,
  ): List<Allocation>

  fun findAllByPersonIdentifierInAndIsActiveTrue(personIdentifiers: Set<String>): List<Allocation>

  fun findAllByPersonIdentifier(personIdentifier: String): List<Allocation>

  @Query(
    """
    with summary as (
        select sa.personIdentifier as pi, sum(case when sa.isActive = true then 1 else 0 end) as active, count(sa) as count
        from Allocation sa
        where sa.personIdentifier in :personIdentifiers 
        and sa.prisonCode = :prisonCode
        group by sa.personIdentifier
    )
    select sum.pi as personIdentifier, sum.active as activeCount, sum.count as totalCount, cur.staffId as staffId
    from summary sum
    left join Allocation cur on cur.personIdentifier = sum.pi and cur.isActive = true
    """,
  )
  fun summariesFor(
    prisonCode: String,
    personIdentifiers: Set<String>,
  ): List<AllocationSummary>

  @Query(
    """
      select a.* from allocation a
      where a.person_identifier = :personIdentifier and a.is_active = true
    """,
    nativeQuery = true,
  )
  fun findCurrentAllocations(personIdentifier: String): List<Allocation>

  @Query(
    """
    with auto_alloc as ( 
        select a.*, row_number() over(partition by a.staff_id order by a.allocated_at desc) as row_number from allocation a 
        where a.staff_id in :staffIds and a.allocation_type = 'A' and a.is_active = true
    )
    select aa.* from auto_alloc aa
    where aa.row_number = 1
    """,
    nativeQuery = true,
  )
  fun findLatestAutoAllocationsFor(staffIds: Set<Long>): List<Allocation>

  @Query(
    """
      select sa.staffId from Allocation sa
      where sa.personIdentifier = :personIdentifier and sa.prisonCode = :prisonCode
      and sa.staffId in :staffIds
      order by sa.allocatedAt desc
    """,
  )
  fun findPreviousAllocations(
    prisonCode: String,
    personIdentifier: String,
    staffIds: Set<Long>,
  ): List<Long>

  @Modifying
  @Query(
    """
    delete from allocation where person_identifier in :personIdentifiers and allocation_type = 'P'
    """,
    nativeQuery = true,
  )
  fun deleteProvisionalFor(personIdentifiers: List<String>)
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
