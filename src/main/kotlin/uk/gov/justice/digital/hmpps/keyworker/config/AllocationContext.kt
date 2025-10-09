package uk.gov.justice.digital.hmpps.keyworker.config

import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote.Companion.KW_ENTRY_SUBTYPE
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote.Companion.KW_TYPE
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote.Companion.PO_ENTRY_SUBTYPE
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote.Companion.PO_ENTRY_TYPE
import java.time.LocalDateTime

data class AllocationContext(
  val username: String,
  val requestAt: LocalDateTime = LocalDateTime.now(),
  val activeCaseloadId: String? = null,
  val policy: AllocationPolicy = AllocationPolicy.KEY_WORKER,
) {
  companion object {
    const val SYSTEM_USERNAME = "SYS"
    const val OMIC_ADMIN_USERNAME = "omicadmin"

    fun get(): AllocationContext = AllocationContextHolder.getContext()
  }
}

enum class AllocationPolicy(
  val nomisUseRoleCode: String?,
  val entryConfig: EntryConfig,
) {
  KEY_WORKER("KW", EntryConfig(KW_TYPE, KW_ENTRY_SUBTYPE)),
  PERSONAL_OFFICER(null, EntryConfig(PO_ENTRY_TYPE, PO_ENTRY_SUBTYPE)),
  ;

  companion object {
    fun of(name: String?): AllocationPolicy? = entries.firstOrNull { it.name.asKeyword() == name?.asKeyword() }
  }
}

private fun String.asKeyword() = lowercase().replace("[_|-]".toRegex(), "")

data class EntryConfig(
  val type: String,
  val subType: String,
)
