package uk.gov.justice.digital.hmpps.keyworker.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.TenantId
import org.hibernate.envers.Audited
import org.hibernate.envers.RelationTargetAuditMode.NOT_AUDITED
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationContext
import uk.gov.justice.digital.hmpps.keyworker.model.StaffStatus
import uk.gov.justice.digital.hmpps.keyworker.utils.IdGenerator
import java.time.LocalDate
import java.util.UUID

@Audited(withModifiedFlag = true)
@Entity
@Table(name = "staff_configuration")
class StaffConfiguration(
  @Audited(targetAuditMode = NOT_AUDITED, withModifiedFlag = true)
  @ManyToOne
  @JoinColumn(name = "status_id")
  var status: ReferenceData,
  var capacity: Int,
  @Column(name = "allow_auto_allocation")
  var allowAutoAllocation: Boolean,
  @Column(name = "reactivate_on")
  var reactivateOn: LocalDate?,
  @Audited(withModifiedFlag = false)
  @Column(name = "staff_id")
  val staffId: Long,
  @TenantId
  @Audited(withModifiedFlag = false)
  @Column(name = "policy_code", updatable = false)
  val policy: String = AllocationContext.get().policy.name,
  @Id
  @Audited(withModifiedFlag = false)
  val id: UUID = IdGenerator.newUuid(),
)

interface StaffConfigRepository : JpaRepository<StaffConfiguration, UUID> {
  @Query(
    """
        with counts as (select kwa.staffId as id, count(kwa) as count
                        from KeyworkerAllocation kwa
                        where kwa.active = true and kwa.allocationType <> 'P'
                        and kwa.prisonCode = :prisonCode and kwa.staffId in :staffIds
                        group by kwa.staffId
        )
        select coalesce(ac.id, config.staffId) as staffId, ac.count as allocationCount, config as staffConfig from counts ac
        full outer join StaffConfiguration config on ac.id = config.staffId
        where config.staffId is null or config.staffId in :staffIds
        """,
  )
  fun findAllWithAllocationCount(
    prisonCode: String,
    staffIds: Set<Long>,
  ): List<KeyworkerWithAllocationCount>

  fun findAllByStaffIdInAndStatusKeyCodeIn(
    staffIds: Set<Long>,
    status: Set<String>,
  ): List<StaffConfiguration>

  fun findByStaffId(staffId: Long): StaffConfiguration?
}

fun StaffConfigRepository.getNonActiveKeyworkers(staffIds: Set<Long>) =
  findAllByStaffIdInAndStatusKeyCodeIn(
    staffIds,
    StaffStatus.entries
      .filter { it != StaffStatus.ACTIVE }
      .map { it.name }
      .toSet(),
  )

interface KeyworkerWithAllocationCount {
  val staffId: Long
  val staffConfig: StaffConfiguration?
  val allocationCount: Int?
}
