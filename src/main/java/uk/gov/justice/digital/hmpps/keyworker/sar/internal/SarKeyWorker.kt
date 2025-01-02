package uk.gov.justice.digital.hmpps.keyworker.sar.internal

import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.Immutable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import uk.gov.justice.digital.hmpps.keyworker.model.AllocationReason
import uk.gov.justice.digital.hmpps.keyworker.model.AllocationType
import uk.gov.justice.digital.hmpps.keyworker.model.AllocationTypeConvertor
import uk.gov.justice.digital.hmpps.keyworker.model.DeallocationReason
import java.time.LocalDateTime

@Immutable
@Entity
@Table(name = "offender_key_worker")
class SarKeyWorker(
  @Column(name = "offender_no")
  val personIdentifier: String,
  @Column(name = "prison_id")
  val prisonCode: String,
  @Column(name = "assigned_date_time")
  val assignedAt: LocalDateTime,
  @Column(name = "expiry_date_time")
  val expiredAt: LocalDateTime?,
  @Column(name = "alloc_type")
  @Convert(converter = AllocationTypeConvertor::class)
  val allocationType: AllocationType,
  @Enumerated(EnumType.STRING)
  @Column(name = "alloc_reason")
  val allocationReason: AllocationReason,
  @Enumerated(EnumType.STRING)
  @Column(name = "dealloc_reason")
  val deallocationReason: DeallocationReason?,
  @Column(name = "staff_id")
  val staffId: Long,
  @Id
  @Column(name = "offender_keyworker_id")
  val id: Long,
)

interface SarKeyWorkerRepository : JpaRepository<SarKeyWorker, Long> {
  @Query(
    """
    select skw from SarKeyWorker skw
    where skw.personIdentifier = :personIdentifier
    and (cast(:from as LocalDateTime) is null or :from <= skw.assignedAt)
    and (cast(:to as LocalDateTime) is null or :to >= skw.assignedAt)
    """,
  )
  fun findSarContent(
    personIdentifier: String,
    from: LocalDateTime?,
    to: LocalDateTime?,
  ): List<SarKeyWorker>
}
