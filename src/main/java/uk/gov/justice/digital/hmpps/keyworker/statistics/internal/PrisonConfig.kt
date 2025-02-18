package uk.gov.justice.digital.hmpps.keyworker.statistics.internal

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDateTime

@Entity
@Table(name = "prison_supported")
class PrisonConfig(
  @Id
  @Column(name = "prison_id")
  val code: String,
  @Column(name = "migrated")
  val migrated: Boolean,
  @Column(name = "migrated_date_time")
  val migratedDateTime: LocalDateTime?,
  @Column(name = "auto_allocate")
  val autoAllocate: Boolean,
  @Column(name = "capacity_tier_1")
  val capacityTier1: Int,
  @Column(name = "capacity_tier_2")
  val capacityTier2: Int?,
  @Column(name = "kw_session_freq_weeks")
  val kwSessionFrequencyInWeeks: Int,
  @Column(name = "has_prisoners_with_high_complexity_needs")
  val hasPrisonersWithHighComplexityNeeds: Boolean,
)

interface PrisonConfigRepository : JpaRepository<PrisonConfig, String> {
  fun findAllByMigratedIsTrue(): List<PrisonConfig>
}
