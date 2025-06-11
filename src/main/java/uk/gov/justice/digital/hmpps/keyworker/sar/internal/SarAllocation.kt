package uk.gov.justice.digital.hmpps.keyworker.sar.internal

import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.Immutable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceData
import uk.gov.justice.digital.hmpps.keyworker.model.AllocationType
import uk.gov.justice.digital.hmpps.keyworker.model.AllocationTypeConvertor
import java.time.LocalDateTime
import java.util.UUID

@Immutable
@Entity
@Table(name = "allocation")
class SarAllocation(
  @Column(name = "person_identifier")
  val personIdentifier: String,
  @Column(name = "prison_code")
  val prisonCode: String,
  @Column(name = "allocated_at")
  val allocatedAt: LocalDateTime,
  @Column(name = "deallocated_at")
  val deallocatedAt: LocalDateTime?,
  @Column(name = "allocation_type")
  @Convert(converter = AllocationTypeConvertor::class)
  val allocationType: AllocationType,
  @ManyToOne
  @JoinColumn(name = "allocation_reason_id")
  val allocationReason: ReferenceData,
  @ManyToOne
  @JoinColumn(name = "deallocation_reason_id")
  val deallocationReason: ReferenceData?,
  @Column(name = "staff_id")
  val staffId: Long,
  @Id
  @Column(name = "id")
  val id: UUID,
)

interface SarKeyWorkerRepository : JpaRepository<SarAllocation, UUID> {
  @Query(
    """
    select skw from SarAllocation skw
    where skw.personIdentifier = :personIdentifier
    and (cast(:from as LocalDateTime) is null or :from <= skw.allocatedAt)
    and (cast(:to as LocalDateTime) is null or :to >= skw.allocatedAt)
    """,
  )
  fun findSarContent(
    personIdentifier: String,
    from: LocalDateTime?,
    to: LocalDateTime?,
  ): List<SarAllocation>
}
