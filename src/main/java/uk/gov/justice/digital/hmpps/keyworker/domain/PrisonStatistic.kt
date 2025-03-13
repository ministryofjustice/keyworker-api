package uk.gov.justice.digital.hmpps.keyworker.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate
import java.util.UUID

@Table(name = "keyworker_stats")
@Entity
class PrisonStatistic(
  @Column(name = "prison_id")
  val prisonCode: String,
  @Column(name = "snapshot_date")
  val date: LocalDate,
  @Column(name = "total_num_prisoners")
  val totalPrisoners: Int,
  @Column(name = "total_num_eligible_prisoners")
  val eligiblePrisoners: Int,
  @Column(name = "num_prisoners_assigned_kw")
  val assignedKeyworker: Int,
  @Column(name = "num_active_keyworkers")
  val activeKeyworkers: Int,
  @Column(name = "num_kw_sessions")
  val keyworkerSessions: Int,
  @Column(name = "num_kw_entries")
  val keyworkerEntries: Int,
  @Column(name = "recpt_to_alloc_days")
  val averageReceptionToAllocationDays: Int?,
  @Column(name = "recpt_to_kw_session_days")
  val averageReceptionToSessionDays: Int?,
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "keyworker_stats_id")
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
