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
import uk.gov.justice.digital.hmpps.keyworker.utils.IdGenerator
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

@Entity
@Audited(withModifiedFlag = true)
@Table(name = "staff_role")
class StaffRole(
  @Audited(withModifiedFlag = true, targetAuditMode = NOT_AUDITED)
  @ManyToOne
  @JoinColumn(name = "position_id")
  var position: ReferenceData,
  @Audited(withModifiedFlag = true, targetAuditMode = NOT_AUDITED)
  @ManyToOne
  @JoinColumn(name = "schedule_type_id")
  var scheduleType: ReferenceData,
  var hoursPerWeek: BigDecimal,
  var fromDate: LocalDate,
  var toDate: LocalDate?,
  @Audited(withModifiedFlag = false)
  @Column(name = "prison_code")
  val prisonCode: String,
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

interface StaffRoleRepository : JpaRepository<StaffRole, UUID> {
  fun findByPrisonCodeAndStaffId(
    prisonCode: String,
    staffId: Long,
  ): StaffRole?

  fun findAllByPrisonCodeAndStaffIdIn(
    prisonCode: String,
    staffIds: Set<Long>,
  ): List<StaffRole>

  fun findAllByPrisonCode(prisonCode: String): List<StaffRole>

  @Query(
    """
        select * from staff_role where prison_code = :prisonCode and staff_id = :staffId and policy_code in :policies
    """,
    nativeQuery = true,
  )
  fun findByPrisonCodeAndStaffIdAndPolicyIn(
    prisonCode: String,
    staffId: Long,
    policies: Set<String>,
  ): List<StaffRole>
}
