package uk.gov.justice.digital.hmpps.keyworker.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.envers.Audited
import org.hibernate.envers.RelationTargetAuditMode.NOT_AUDITED
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import uk.gov.justice.digital.hmpps.keyworker.model.KeyworkerStatus
import java.time.LocalDate

@Audited(withModifiedFlag = true)
@Entity
@Table(name = "keyworker_configuration")
class KeyworkerConfig(
  @Audited(targetAuditMode = NOT_AUDITED, withModifiedFlag = true)
  @ManyToOne
  @JoinColumn(name = "status_id")
  var status: ReferenceData,
  var capacity: Int,
  @Column(name = "auto_allocation")
  var autoAllocation: Boolean,
  @Column(name = "reactivate_on")
  var reactivateOn: LocalDate?,
  @Audited(withModifiedFlag = false)
  @Id
  @Column(name = "staff_id")
  val staffId: Long,
)

interface KeyworkerConfigRepository : JpaRepository<KeyworkerConfig, Long> {
  @Query(
    """
        with counts as (select kwa.staffId as id, count(kwa) as count
                        from KeyworkerAllocation kwa
                        where kwa.active = true and kwa.allocationType <> 'P'
                        and kwa.prisonCode = :prisonCode and kwa.staffId in :staffIds
                        group by kwa.staffId
        )
        select coalesce(ac.id, config.staffId) as staffId, ac.count as allocationCount, config as keyworkerConfig from counts ac
        full outer join KeyworkerConfig config on ac.id = config.staffId
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
  ): List<KeyworkerConfig>
}

fun KeyworkerConfigRepository.getNonActiveKeyworkers(staffIds: Set<Long>) =
  findAllByStaffIdInAndStatusKeyCodeIn(
    staffIds,
    KeyworkerStatus.entries
      .filter { it != KeyworkerStatus.ACTIVE }
      .map { it.name }
      .toSet(),
  )

interface KeyworkerWithAllocationCount {
  val staffId: Long
  val keyworkerConfig: KeyworkerConfig?
  val allocationCount: Int?
}
