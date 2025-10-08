package uk.gov.justice.digital.hmpps.keyworker.dto

enum class AllocationReason {
  AUTO,
  MANUAL,
}

enum class DeallocationReason {
  OVERRIDE,
  RELEASED,
  STAFF_STATUS_CHANGE,
  TRANSFER,
  MERGED,
  MISSING,
  DUPLICATE,
  MANUAL,
  CHANGE_IN_COMPLEXITY_OF_NEED,
  NO_LONGER_IN_PRISON,
  PRISON_USES_KEY_WORK,
  MIGRATION,
}
