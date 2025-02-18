package uk.gov.justice.digital.hmpps.keyworker.statistics.internal

import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import uk.gov.justice.digital.hmpps.keyworker.model.KeyworkerStatus
import uk.gov.justice.digital.hmpps.keyworker.model.KeyworkerStatusConvertor

@Entity
@Table(name = "key_worker")
class Keyworker(
  @Column(name = "status")
  @Convert(converter = KeyworkerStatusConvertor::class)
  val status: KeyworkerStatus,
  val capacity: Int,
  @Id @Column(name = "staff_id")
  val staffId: Long,
)

interface KeyworkerRepository : JpaRepository<Keyworker, Long> {
  fun countAllByStaffIdInAndStatus(
    staffIds: Set<Long>,
    status: KeyworkerStatus,
  ): Int
}

fun KeyworkerRepository.countActiveKeyworkers(staffIds: Set<Long>) = countAllByStaffIdInAndStatus(staffIds, KeyworkerStatus.ACTIVE)
