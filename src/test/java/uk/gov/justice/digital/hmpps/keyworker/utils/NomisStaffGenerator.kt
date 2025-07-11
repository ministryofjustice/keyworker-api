package uk.gov.justice.digital.hmpps.keyworker.utils

import uk.gov.justice.digital.hmpps.keyworker.dto.StaffLocationRoleDto
import uk.gov.justice.digital.hmpps.keyworker.dto.StaffSummary
import uk.gov.justice.digital.hmpps.keyworker.integration.nomisuserroles.NomisStaff
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

  fun staffLocationRole(
    staffId: Long,
    toDate: LocalDate? = null,
  ): StaffLocationRoleDto =
    StaffLocationRoleDto
      .builder()
      .staffId(staffId)
      .firstName("First Name $staffId")
      .lastName("Last Name $staffId")
      .position(positionTypes.random())
      .scheduleType(scheduleTypes.random())
      .hoursPerWeek(BigDecimal.valueOf(37.5))
      .fromDate(LocalDate.now().minusDays(staffId * 64))
      .toDate(toDate)
      .build()

  fun staffRoles(staffIds: List<Long>): List<StaffLocationRoleDto> = staffIds.map { staffLocationRole(it) }

  fun staffSummaries(staffIds: Set<Long>): List<StaffSummary> = staffIds.map { StaffSummary(it, "First$it", "Last$it") }
}
