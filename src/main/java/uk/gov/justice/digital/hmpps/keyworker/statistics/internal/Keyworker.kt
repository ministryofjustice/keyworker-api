package uk.gov.justice.digital.hmpps.keyworker.statistics.internal

import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.type.YesNoConverter
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import uk.gov.justice.digital.hmpps.keyworker.model.KeyworkerStatus
import uk.gov.justice.digital.hmpps.keyworker.model.KeyworkerStatusConvertor

@Entity
@Table(name = "key_worker")
class Keyworker(
  @Column(name = "status")
  @Convert(converter = KeyworkerStatusConvertor::class)
  val status: KeyworkerStatus,
  val capacity: Int,
  @Column(name = "auto_allocation_flag")
  @Convert(converter = YesNoConverter::class)
  val autoAllocation: Boolean,
  @Id @Column(name = "staff_id")
  val staffId: Long,
)

interface KeyworkerRepository : JpaRepository<Keyworker, Long> {
  @Query(
    """
        with counts as (select kwa.staffId as id, count(kwa) as count
                        from KeyworkerAllocation kwa
                        where kwa.active = true and kwa.allocationType <> 'P'
                        and kwa.staffId in :staffIds
                        group by kwa.staffId
        )
        select ac.id as staffId, ac.count as allocationCount, kw as keyworker from counts ac
        full outer join Keyworker kw on ac.id = kw.staffId
        where kw.staffId in :staffIds
        """,
  )
  fun findAllWithAllocationCount(staffIds: Set<Long>): List<KeyworkerWithAllocationCount>

  fun findAllByStaffIdIn(staffId: Set<Long>): List<Keyworker>
}

interface KeyworkerWithAllocationCount {
  val staffId: Long
  val keyworker: Keyworker?
  val allocationCount: Int?
}
