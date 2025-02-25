package uk.gov.justice.digital.hmpps.keyworker.dto

enum class ScheduleType(
  val code: String,
  val description: String,
) {
  FULL_TIME("FT", "Full Time"),
  PART_TIME("PT", "Part Time"),
  SESSIONAL("SESS", "Sessional"),
  VOLUNTEER("VOL", "Volunteer"),
  UNKNOWN("UNK", "Unknown"),
  ;

  companion object {
    fun from(code: String): ScheduleType = entries.find { it.code == code } ?: UNKNOWN
  }
}
