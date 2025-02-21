package uk.gov.justice.digital.hmpps.keyworker.statistics.internal

import jakarta.persistence.Entity
import jakarta.persistence.Id
import org.springframework.data.jpa.repository.JpaRepository
import uk.gov.justice.digital.hmpps.keyworker.utils.IdGenerator.newUuid
import java.time.LocalDate
import java.util.UUID

@Entity
class PrisonStatistic(
  val prisonCode: String,
  val date: LocalDate,
  val totalPrisoners: Int,
  val eligiblePrisoners: Int,
  val assignedKeyworker: Int,
  val activeKeyworkers: Int,
  val keyworkerSessions: Int,
  val keyworkerEntries: Int,
  val averageReceptionToAllocationDays: Int?,
  val averageReceptionToSessionDays: Int?,
  @Id
  val id: UUID = newUuid(),
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
