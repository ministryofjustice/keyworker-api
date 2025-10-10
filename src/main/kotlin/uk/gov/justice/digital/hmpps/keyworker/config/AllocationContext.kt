package uk.gov.justice.digital.hmpps.keyworker.config

import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationContext.Companion.SYSTEM_USERNAME
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

    fun clear() {
      AllocationContextHolder.clearContext()
    }
  }
}

fun AllocationContext.set() = apply { AllocationContextHolder.setContext(this) }

@Component
class AllocationContextHolder {
  fun setContext(context: AllocationContext) {
    AllocationContextHolder.context.set(context)
  }

  companion object {
    private var context: ThreadLocal<AllocationContext> =
      ThreadLocal.withInitial { AllocationContext(SYSTEM_USERNAME) }

    internal fun getContext(): AllocationContext = context.get()

    internal fun setContext(ctx: AllocationContext) {
      context.set(ctx)
    }

    internal fun clearContext() {
      context.remove()
    }
  }
}

enum class AllocationPolicy(
  val nomisUserRoleCode: String?,
) {
  KEY_WORKER("KW"),
  PERSONAL_OFFICER(null),
  ;

  companion object {
    private fun String.asKeyword() = lowercase().replace("[_|-]".toRegex(), "")

    fun of(name: String?): AllocationPolicy? = entries.firstOrNull { it.name.asKeyword() == name?.asKeyword() }
  }
}
