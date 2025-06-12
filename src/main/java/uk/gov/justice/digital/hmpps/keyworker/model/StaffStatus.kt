package uk.gov.justice.digital.hmpps.keyworker.model

import uk.gov.justice.digital.hmpps.keyworker.dto.CodedDescription

enum class StaffStatus(
  val statusCode: String,
  val description: String,
) {
  ACTIVE("ACT", "Active"),
  UNAVAILABLE_ANNUAL_LEAVE("UAL", "Unavailable - annual leave"),
  UNAVAILABLE_LONG_TERM_ABSENCE("ULT", "Unavailable - long term absence"),
  UNAVAILABLE_NO_PRISONER_CONTACT("UNP", "Unavailable - no prisoner contact"),
  INACTIVE("INA", "Inactive"),
  ;

  fun codedDescription(): CodedDescription = CodedDescription(statusCode, description)

  companion object {
    private val lookup = entries.associateBy(StaffStatus::statusCode)

    operator fun get(statusCode: String): StaffStatus? = lookup[statusCode]
  }
}
