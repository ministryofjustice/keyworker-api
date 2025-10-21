package uk.gov.justice.digital.hmpps.keyworker.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.SequenceGenerator
import jakarta.persistence.Table
import org.hibernate.annotations.TenantId
import org.springframework.data.jpa.repository.JpaRepository
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationContext
import java.time.LocalDate

@Entity
@Table(name = "prisoner_statistic")
@SequenceGenerator(
  name = "prisoner_statistic_id_generator",
  sequenceName = "prisoner_statistic_id_seq",
  allocationSize = 1,
)
class PrisonerStatistic(
  @ManyToOne
  @JoinColumn(name = "prison_statistic_id")
  val prisonStatistic: PrisonStatistic,
  val personIdentifier: String,
  val cellLocation: String?,
  val allocationEligibilityDate: LocalDate?,
  @TenantId
  @Column(name = "policy_code", updatable = false)
  val policy: String = AllocationContext.get().requiredPolicy().name,
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "prisoner_statistic_id_generator")
  @Column(name = "id")
  val id: Long? = null,
)

interface PrisonerStatisticRepository : JpaRepository<PrisonerStatistic, Long>
