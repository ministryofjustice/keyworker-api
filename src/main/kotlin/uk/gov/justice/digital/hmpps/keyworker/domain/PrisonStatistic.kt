package uk.gov.justice.digital.hmpps.keyworker.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.TenantId
import org.springframework.data.jpa.repository.JpaRepository
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationContext
import java.time.LocalDate
import java.util.UUID

@Table(name = "prison_statistic")
@Entity
class PrisonStatistic(
  @Column(name = "prison_code")
  val prisonCode: String,
  @Column(name = "statistic_date")
  val date: LocalDate,
  @Column(name = "prisoner_count")
  val prisonerCount: Int,
  @Column(name = "high_complexity_of_need_prisoner_count")
  val highComplexityOfNeedPrisonerCount: Int,
  @Column(name = "eligible_prisoner_count")
  val eligiblePrisonerCount: Int,
  @Column(name = "prisoners_assigned_count")
  val prisonersAssignedCount: Int,
  @Column(name = "eligible_staff_count")
  val eligibleStaffCount: Int,
  @Column(name = "reception_to_allocation_days")
  val receptionToAllocationDays: Int?,
  @Column(name = "reception_to_recorded_event_days")
  val receptionToRecordedEventDays: Int?,
  @TenantId
  @Column(name = "policy_code", updatable = false)
  val policy: String = AllocationContext.get().requiredPolicy().name,
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id")
  val id: Long? = null,
)

interface PrisonStatisticRepository : JpaRepository<PrisonStatistic, UUID> {
  fun findByPrisonCodeAndDate(
    prisonCode: String,
    date: LocalDate,
  ): PrisonStatistic?

  fun findAllByPrisonCodeAndDateBetween(
    prisonCode: String,
    from: LocalDate,
    to: LocalDate,
  ): List<PrisonStatistic>
}
