package uk.gov.justice.digital.hmpps.keyworker.config

import java.time.LocalDateTime

data class AllocationContext(
  val username: String,
  val requestAt: LocalDateTime = LocalDateTime.now(),
  val activeCaseloadId: String? = null,
  val policy: AllocationPolicy = AllocationPolicy.KEY_WORKER,
) {
  companion object {
    const val SYSTEM_USER_NAME = "SYS"

    fun get(): AllocationContext = AllocationContextHolder.getContext()
  }
}

enum class AllocationPolicy(
  val nomisUseRoleCode: String?,
) {
  KEY_WORKER("KW"),
  PERSONAL_OFFICER(null),
  ;

  companion object {
    fun of(name: String?): AllocationPolicy = entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: KEY_WORKER
  }
}
