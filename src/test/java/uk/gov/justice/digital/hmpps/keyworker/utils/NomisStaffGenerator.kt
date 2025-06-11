package uk.gov.justice.digital.hmpps.keyworker.utils

import uk.gov.justice.digital.hmpps.keyworker.dto.StaffLocationRoleDto
import uk.gov.justice.digital.hmpps.keyworker.integration.nomisuserroles.NomisStaff
import java.math.BigDecimal
import java.time.LocalDate

object NomisStaffGenerator {
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

  fun staffRoles(staffIds: List<Long>): List<StaffLocationRoleDto> {
    val positionTypes = listOf("AA", "AO", "PPO", "PRO", "CHAP")
    val scheduleTypes = listOf("FT", "PT", "SESS", "VOL")
    return staffIds.map {
      StaffLocationRoleDto
        .builder()
        .staffId(it)
        .firstName("First Name $it")
        .lastName("Last Name $it")
        .position(positionTypes.random())
        .scheduleType(scheduleTypes.random())
        .hoursPerWeek(BigDecimal.valueOf(37.5))
        .fromDate(LocalDate.now().minusDays(it * 64))
        .build()
    }
  }
}
