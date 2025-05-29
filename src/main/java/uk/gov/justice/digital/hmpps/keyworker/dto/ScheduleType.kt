package uk.gov.justice.digital.hmpps.keyworker.dto

enum class ScheduleType(
  val code: String,
) {
  FULL_TIME("FT"),
  PART_TIME("PT"),
  SESSIONAL("SESS"),
  VOLUNTEER("VOL"),
  UNKNOWN("UNK"),
}
