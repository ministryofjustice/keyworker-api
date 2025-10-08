package uk.gov.justice.digital.hmpps.keyworker.utils

import uk.gov.justice.digital.hmpps.keyworker.dto.NomisStaffRole
import uk.gov.justice.digital.hmpps.keyworker.dto.StaffSummary
import uk.gov.justice.digital.hmpps.keyworker.integration.nomisuserroles.NomisStaff
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisIdGenerator.newId
import java.math.BigDecimal
import java.time.LocalDate

object NomisStaffGenerator {
  val positionTypes = listOf("AA", "AO", "PPO", "PRO", "CHAP")
  val scheduleTypes = listOf("FT", "PT", "SESS", "VOL")

  fun generate(
    staffId: Long,
    status: String = "ACTIVE",
  ): NomisStaff =
    NomisStaff(
      "user-$staffId",
      "user-$staffId@email.co.uk",
      staffId,
      "First$staffId",
      "Last$staffId",
      status,
    )

  fun fromStaffIds(staffIds: List<Long>): List<NomisStaff> = staffIds.map { generate(it) }

  fun nomisStaffRole(
    staffId: Long = newId(),
    firstName: (Long) -> String = { "First Name $it" },
    lastName: (Long) -> String = { "Last Name $it" },
    position: String = positionTypes.random(),
    scheduleType: String = scheduleTypes.random(),
    hoursPerWeek: BigDecimal = BigDecimal.valueOf(37.5),
    fromDate: LocalDate = LocalDate.now().minusDays(staffId * 64),
    toDate: LocalDate? = null,
  ): NomisStaffRole =
    NomisStaffRole(
      staffId,
      firstName(staffId),
      lastName(staffId),
      position,
      scheduleType,
      hoursPerWeek,
      fromDate,
      toDate,
    )

  fun nomisStaffRoles(
    staffIds: List<Long>,
    firstName: (Long) -> String = { "First Name $it" },
    lastName: (Long) -> String = { "Last Name $it" },
  ): List<NomisStaffRole> = staffIds.map { nomisStaffRole(it, firstName = firstName, lastName = lastName) }

  fun staffSummaries(staffIds: Set<Long>): List<StaffSummary> = staffIds.map { StaffSummary(it, "First Name $it", "Last Name $it") }
}
